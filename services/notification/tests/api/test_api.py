import uuid

import pytest
from fastapi.testclient import TestClient

from app.auth.dependencies import get_token_verifier
from app.auth.keycloak import KeycloakTokenVerifier
from app.config import Settings
from app.main import create_app
from app.models import Notification, ProcessedEvent
from app.models.enums import Channel, DeliveryStatus
from tests.conftest import TEST_AUDIENCE, TEST_ISSUER

pytestmark = pytest.mark.integration


@pytest.fixture
def client(session_factory, signing_keys) -> TestClient:
    app = create_app(Settings(environment="test"))
    public_key = signing_keys.private_key.public_key()
    app.dependency_overrides[get_token_verifier] = lambda: KeycloakTokenVerifier(
        Settings(oidc_issuer=TEST_ISSUER, oidc_audience=TEST_AUDIENCE), key_resolver=lambda _: public_key
    )
    return TestClient(app)


def auth(make_token, user_id: uuid.UUID) -> dict[str, str]:
    return {"Authorization": f"Bearer {make_token(sub=user_id)}"}


def add_notification(session, recipient_id: uuid.UUID, channel: Channel = Channel.IN_APP) -> Notification:
    event = ProcessedEvent(event_id=uuid.uuid4(), event_type="ExamResultsPublished")
    notification = Notification(
        recipient_id=recipient_id,
        channel=channel,
        event_type="ExamResultsPublished",
        title="TYT Deneme 3 sonucun açıklandı",
        body="...",
        status=DeliveryStatus.SENT,
        source_event_id=event.event_id,
    )
    session.add(event)
    session.flush()  # no ORM relationship: write the referenced row first
    session.add(notification)
    session.commit()
    return notification


def test_users_see_and_read_only_their_own_in_app_notifications(client, db_session, make_token) -> None:
    me, someone_else = uuid.uuid4(), uuid.uuid4()
    mine = add_notification(db_session, me)
    add_notification(db_session, me, Channel.EMAIL)  # e-mail rows are not part of the inbox
    theirs = add_notification(db_session, someone_else)
    headers = auth(make_token, me)

    listed = client.get("/api/v1/notifications", headers=headers).json()
    assert [item["id"] for item in listed] == [str(mine.id)]
    assert listed[0]["readAt"] is None
    assert client.get("/api/v1/notifications/unread-count", headers=headers).json() == {"count": 1}

    assert client.post(f"/api/v1/notifications/{mine.id}/read", headers=headers).status_code == 204
    assert client.get("/api/v1/notifications/unread-count", headers=headers).json() == {"count": 0}

    # Someone else's notification answers like a missing one.
    response = client.post(f"/api/v1/notifications/{theirs.id}/read", headers=headers)
    assert response.status_code == 404
    assert response.headers["content-type"] == "application/problem+json"
    assert response.json()["errorCode"] == "NOTIFICATION_NOT_FOUND"
    assert response.json()["detail"] == "Bildirim bulunamadı."


def test_read_all(client, db_session, make_token) -> None:
    me = uuid.uuid4()
    add_notification(db_session, me)
    add_notification(db_session, me)

    assert client.post("/api/v1/notifications/read-all", headers=auth(make_token, me)).json() == {
        "updated": 2
    }


def test_preferences_turn_email_and_sms_off_but_never_in_app(client, make_token) -> None:
    headers = auth(make_token, uuid.uuid4())
    assert all(
        item["enabled"] for item in client.get("/api/v1/notification-preferences", headers=headers).json()
    )

    body = {"preferences": [{"eventType": "ExamResultsPublished", "channel": "SMS", "enabled": False}]}
    saved = client.put("/api/v1/notification-preferences", json=body, headers=headers).json()
    assert {(item["channel"], item["enabled"]) for item in saved} == {("EMAIL", True), ("SMS", False)}

    body = {"preferences": [{"eventType": "ExamResultsPublished", "channel": "IN_APP", "enabled": False}]}
    response = client.put(
        "/api/v1/notification-preferences", json=body, headers={**headers, "Accept-Language": "en"}
    )
    assert response.status_code == 422
    assert response.json()["errorCode"] == "CHANNEL_NOT_CONFIGURABLE"
    assert response.json()["detail"].startswith("In-app notifications cannot be turned off")


def test_missing_token_is_a_problem_detail_in_turkish(client) -> None:
    response = client.get("/api/v1/notifications")

    assert response.status_code == 401
    assert response.json() == {
        "type": "about:blank",
        "title": "Unauthorized",
        "status": 401,
        "detail": "Bu işlem için giriş yapmanız gerekiyor.",
        "errorCode": "TOKEN_MISSING",
    }


def test_swagger_is_closed_outside_local_and_dev(client) -> None:
    assert client.get("/docs").status_code == 404
    assert client.get("/health/ready").json()["status"] == "ok"
