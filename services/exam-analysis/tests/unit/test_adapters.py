import json
import uuid
from datetime import UTC, date, datetime
from pathlib import Path

import boto3
import pytest
import redis
from jsonschema import Draft202012Validator
from moto import mock_aws

from app.auth.keycloak import KeycloakTokenVerifier
from app.cache import RedisResultCache
from app.config import Settings
from app.errors import NotFoundError, UnauthorizedError
from app.events.payloads import (
    SOURCE,
    EventEnvelope,
    ExamResultsPublished,
    StudentAddedToClassroom,
    StudentResultAdded,
)
from app.events.publisher import SnsEventPublisher
from app.models.enums import Role
from app.storage.file_storage import S3FileStorage
from tests.conftest import TEST_AUDIENCE, TEST_ISSUER

EVENTS_DIR = Path(__file__).resolve().parents[4] / "shared" / "events"
REGION = "eu-central-1"


class TestKeycloakTokenVerifier:
    @pytest.fixture
    def verifier(self, signing_keys) -> KeycloakTokenVerifier:
        public_key = signing_keys.private_key.public_key()
        settings = Settings(oidc_issuer=TEST_ISSUER, oidc_audience=TEST_AUDIENCE)
        return KeycloakTokenVerifier(settings, key_resolver=lambda _: public_key)

    def test_valid_token_yields_user_roles_and_branch(self, verifier, make_token) -> None:
        user_id, branch_id = uuid.uuid4(), uuid.uuid4()

        user = verifier.verify(make_token(sub=user_id, roles=["teacher"], branch_id=branch_id))

        assert user.id == user_id
        assert user.roles == frozenset({Role.TEACHER})  # Keycloak's default roles are dropped
        assert user.branch_id == branch_id

    @pytest.mark.parametrize(
        ("overrides", "code"),
        [
            ({"expires_in": -10}, "TOKEN_EXPIRED"),
            ({"issuer": "http://evil.example/realms/dershane"}, "TOKEN_INVALID"),
            ({"audience": "another-service"}, "TOKEN_INVALID"),
        ],
    )
    def test_invalid_tokens_are_rejected(self, verifier, make_token, overrides, code) -> None:
        with pytest.raises(UnauthorizedError) as error:
            verifier.verify(make_token(sub=uuid.uuid4(), roles=["student"], **overrides))

        assert error.value.code == code

    def test_super_admin_role_is_read(self, verifier, make_token) -> None:
        user = verifier.verify(make_token(sub=uuid.uuid4(), roles=["admin", "super-admin"]))

        assert user.roles == frozenset({Role.ADMIN, Role.SUPER_ADMIN})

    def test_token_signed_with_another_key_is_rejected(self, verifier) -> None:
        import jwt
        from cryptography.hazmat.primitives.asymmetric import rsa

        other_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        token = jwt.encode(
            {"sub": str(uuid.uuid4()), "iss": TEST_ISSUER, "aud": TEST_AUDIENCE, "iat": 0, "exp": 9999999999},
            other_key,
            algorithm="RS256",
        )

        with pytest.raises(UnauthorizedError):
            verifier.verify(token)

    def test_non_uuid_branch_claim_is_rejected(self, verifier, signing_keys) -> None:
        import jwt

        token = jwt.encode(
            {
                "sub": str(uuid.uuid4()),
                "iss": TEST_ISSUER,
                "aud": TEST_AUDIENCE,
                "iat": 0,
                "exp": 9999999999,
                "branch_id": "x",
            },
            signing_keys.private_key,
            algorithm="RS256",
        )

        with pytest.raises(UnauthorizedError) as error:
            verifier.verify(token)
        assert error.value.context == {"claim": "branch_id"}


class TestRedisResultCache:
    def test_unavailable_redis_is_a_logged_miss(self, mocker, caplog) -> None:
        client = mocker.Mock()
        client.get.side_effect = redis.ConnectionError("down")
        cache = RedisResultCache(client, ttl_seconds=60)

        assert cache.get_json("key") is None
        assert any("cache.unavailable" in str(record.msg) for record in caplog.records)

    def test_values_round_trip_as_json(self, mocker) -> None:
        store: dict[str, str] = {}
        client = mocker.Mock()
        client.set.side_effect = lambda key, value, ex: store.__setitem__(key, value)
        client.get.side_effect = store.get
        cache = RedisResultCache(client, ttl_seconds=60)

        cache.set_json("k", {"a": 1})

        assert cache.get_json("k") == {"a": 1}
        client.set.assert_called_once_with("k", '{"a": 1}', ex=60)


@mock_aws
class TestAwsAdapters:
    def test_s3_storage_round_trip_and_missing_file(self) -> None:
        client = boto3.client("s3", region_name=REGION)
        client.create_bucket(Bucket="optical", CreateBucketConfiguration={"LocationConstraint": REGION})
        storage = S3FileStorage(client, "optical")
        file_id = uuid.uuid4()

        storage.put(file_id, b"1001A", "text/plain")

        assert storage.get(file_id) == b"1001A"
        with pytest.raises(NotFoundError):
            storage.get(uuid.uuid4())

    def test_sns_publisher_sends_event_type_and_traceparent_attributes(self) -> None:
        sns = boto3.client("sns", region_name=REGION)
        sqs = boto3.client("sqs", region_name=REGION)
        topic_arn = sns.create_topic(Name="exam-analysis-events")["TopicArn"]
        queue_url = sqs.create_queue(QueueName="notification")["QueueUrl"]
        queue_arn = sqs.get_queue_attributes(QueueUrl=queue_url, AttributeNames=["QueueArn"])["Attributes"][
            "QueueArn"
        ]
        sns.subscribe(
            TopicArn=topic_arn, Protocol="sqs", Endpoint=queue_arn, Attributes={"RawMessageDelivery": "true"}
        )
        envelope = {"eventId": str(uuid.uuid4()), "eventType": "StudentResultAdded", "data": {}}
        traceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"

        SnsEventPublisher(sns, topic_arn).publish(envelope, traceparent)

        messages = sqs.receive_message(QueueUrl=queue_url, MessageAttributeNames=["All"])["Messages"]
        assert json.loads(messages[0]["Body"]) == envelope
        attributes = messages[0]["MessageAttributes"]
        assert attributes["eventType"]["StringValue"] == "StudentResultAdded"
        assert attributes["traceparent"]["StringValue"] == traceparent


class TestEventContracts:
    """Envelopes and data must validate against shared/events, the cross-language contract."""

    @staticmethod
    def validator(relative_path: str) -> Draft202012Validator:
        return Draft202012Validator(json.loads((EVENTS_DIR / relative_path).read_text()))

    def envelope(self, payload) -> dict:
        return EventEnvelope(
            event_id=uuid.uuid4(),
            event_type=type(payload).__name__,
            occurred_at=datetime.now(UTC),
            data=payload.model_dump(mode="json", by_alias=True),
        ).model_dump(mode="json", by_alias=True)

    def assert_published_contract(self, payload) -> dict:
        envelope = self.envelope(payload)
        self.validator("envelope.schema.json").validate(envelope)
        self.validator(f"{SOURCE}/{envelope['eventType']}.schema.json").validate(envelope["data"])
        return envelope

    def test_exam_results_published(self) -> None:
        envelope = self.assert_published_contract(
            ExamResultsPublished(
                mock_exam_id=uuid.uuid4(),
                branch_id=uuid.uuid4(),
                exam_type_code="TYT",
                student_ids=[uuid.uuid4()],
            )
        )

        assert envelope["source"] == SOURCE
        assert envelope["schemaVersion"] == 1
        assert envelope["occurredAt"].endswith("Z")

    def test_student_result_added(self) -> None:
        self.assert_published_contract(
            StudentResultAdded(mock_exam_id=uuid.uuid4(), branch_id=uuid.uuid4(), student_id=uuid.uuid4())
        )

    def test_consumed_student_added_to_classroom_matches_our_model(self) -> None:
        data = {
            "membershipId": str(uuid.uuid4()),
            "studentId": str(uuid.uuid4()),
            "classroomId": str(uuid.uuid4()),
            "branchId": str(uuid.uuid4()),
            "studentNumber": "1001",
            "fullName": "X",
            "startDate": "2026-10-25",
        }
        self.validator("user/StudentAddedToClassroom.schema.json").validate(data)

        event = StudentAddedToClassroom.model_validate(data)

        assert (event.student_number, event.start_date) == ("1001", date(2026, 10, 25))
        assert event.model_dump(mode="json", by_alias=True) == data
