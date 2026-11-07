import uuid
from datetime import timedelta

import pytest

from app.config import Settings
from app.delivery.dispatcher import Dispatcher
from app.delivery.senders import PermanentDeliveryError, TemporaryDeliveryError
from app.models import Notification, ProcessedEvent
from app.models.base import utcnow
from app.models.enums import Channel, DeliveryStatus, UserRole
from tests.factories import contact

pytestmark = pytest.mark.integration


class FakeSender:
    """Records sends; raises `error` instead when it is set."""

    def __init__(self, error: Exception | None = None) -> None:
        self.error = error
        self.sent: list[tuple] = []

    def send(self, to: str, *texts: str) -> None:
        if self.error is not None:
            raise self.error
        self.sent.append((to, *texts))


def pending(
    session, recipient_id: uuid.UUID, channel: Channel = Channel.EMAIL, attempts: int = 0
) -> Notification:
    event = ProcessedEvent(event_id=uuid.uuid4(), event_type="ExamResultsPublished")
    notification = Notification(
        recipient_id=recipient_id,
        channel=channel,
        event_type="ExamResultsPublished",
        title="Başlık",
        body="Gövde",
        status=DeliveryStatus.PENDING,
        attempt_count=attempts,
        source_event_id=event.event_id,
    )
    session.add(event)
    session.flush()  # no ORM relationship: write the referenced row first
    session.add(notification)
    session.commit()
    return notification


def dispatcher(session_factory, email: FakeSender, sms: FakeSender | None = None) -> Dispatcher:
    settings = Settings(delivery_max_attempts=3, delivery_backoff_seconds=[60, 300])
    return Dispatcher(session_factory, email, sms or FakeSender(), settings)


@pytest.fixture
def fatma(db_session) -> uuid.UUID:
    person = contact(uuid.uuid4(), UserRole.GUARDIAN, "Fatma", email="fatma@example.com", phone="05320000001")
    db_session.add(person)
    db_session.commit()
    return person.user_id


def test_due_email_and_sms_are_sent(session_factory, db_session, fatma) -> None:
    email_row, sms_row = pending(db_session, fatma), pending(db_session, fatma, Channel.SMS)
    email, sms = FakeSender(), FakeSender()

    assert dispatcher(session_factory, email, sms).deliver_batch() == 2

    assert email.sent == [("fatma@example.com", "Başlık", "Gövde")]
    assert sms.sent == [("05320000001", "Gövde")]
    for row in (email_row, sms_row):
        db_session.refresh(row)
        assert (row.status, row.attempt_count) == (DeliveryStatus.SENT, 1)


def test_temporary_error_schedules_a_retry_then_fails_after_max_attempts(
    session_factory, db_session, fatma
) -> None:
    row = pending(db_session, fatma)
    broken = FakeSender(TemporaryDeliveryError("ConnectionRefusedError"))

    dispatcher(session_factory, broken).deliver_batch()

    db_session.refresh(row)
    assert (row.status, row.attempt_count, row.last_error) == (
        DeliveryStatus.PENDING,
        1,
        "ConnectionRefusedError",
    )
    assert row.next_attempt_at > utcnow() + timedelta(seconds=50)
    assert dispatcher(session_factory, broken).deliver_batch() == 0  # not due yet

    row.next_attempt_at, row.attempt_count = None, 2
    db_session.commit()
    dispatcher(session_factory, broken).deliver_batch()

    db_session.refresh(row)
    assert (row.status, row.attempt_count) == (DeliveryStatus.FAILED, 3)


def test_permanent_error_fails_at_once(session_factory, db_session, fatma) -> None:
    row = pending(db_session, fatma)

    dispatcher(session_factory, FakeSender(PermanentDeliveryError("SMTP 550"))).deliver_batch()

    db_session.refresh(row)
    assert (row.status, row.last_error) == (DeliveryStatus.FAILED, "SMTP 550")


def test_recipient_without_address_is_undeliverable(session_factory, db_session, caplog) -> None:
    person = contact(uuid.uuid4(), UserRole.STUDENT, "Ali")  # e-mail removed after the row was written
    db_session.add(person)
    db_session.commit()
    row = pending(db_session, person.user_id)
    email = FakeSender()

    dispatcher(session_factory, email).deliver_batch()

    db_session.refresh(row)
    assert (row.status, row.last_error, email.sent) == (
        DeliveryStatus.FAILED,
        "undeliverable: no_address",
        [],
    )
    assert any("notification.undeliverable" in str(record.msg) for record in caplog.records)
