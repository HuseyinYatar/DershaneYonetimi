import json
import uuid
from datetime import UTC, datetime

import boto3
import pytest
from alembic.autogenerate import compare_metadata
from alembic.migration import MigrationContext
from moto import mock_aws
from sqlalchemy import func, select

from app.db import get_engine
from app.events.consumer import EventConsumer
from app.events.handlers import EventHandlers
from app.events.queue import SqsQueue
from app.models import Base, ContactReplica, GuardianLinkReplica, Notification, ProcessedEvent
from app.models.enums import Channel, DeliveryStatus
from tests.factories import create_scenario

pytestmark = pytest.mark.integration


def test_migrations_match_the_models(migrated_database: str) -> None:
    with get_engine().connect() as connection:
        diff = compare_metadata(
            MigrationContext.configure(connection, opts={"compare_type": True}), Base.metadata
        )

    assert diff == []


def envelope(event_type: str, data: dict, event_id: uuid.UUID | None = None, source: str = "user") -> dict:
    return {
        "eventId": str(event_id or uuid.uuid4()),
        "eventType": event_type,
        "schemaVersion": 1,
        "occurredAt": datetime.now(UTC).isoformat().replace("+00:00", "Z"),
        "source": source,
        "data": data,
    }


class QueueHarness:
    """A moto SQS queue plus the consumer reading it."""

    def __init__(self, client, queue_url: str, session_factory) -> None:
        self.client = client
        self.queue_url = queue_url
        self.consumer = EventConsumer(
            session_factory,
            SqsQueue(client, queue_url, max_messages=10, wait_seconds=0),
            EventHandlers("http://fe"),
        )

    def send(self, body: dict | str) -> None:
        text = body if isinstance(body, str) else json.dumps(body)
        attributes = {}
        if isinstance(body, dict):
            attributes["eventType"] = {"DataType": "String", "StringValue": body["eventType"]}
        self.client.send_message(QueueUrl=self.queue_url, MessageBody=text, MessageAttributes=attributes)

    def messages_left(self) -> int:
        attributes = self.client.get_queue_attributes(
            QueueUrl=self.queue_url,
            AttributeNames=["ApproximateNumberOfMessages", "ApproximateNumberOfMessagesNotVisible"],
        )["Attributes"]
        return int(attributes["ApproximateNumberOfMessages"]) + int(
            attributes["ApproximateNumberOfMessagesNotVisible"]
        )


@pytest.fixture
def harness(session_factory):
    with mock_aws():
        client = boto3.client("sqs", region_name="eu-central-1")
        queue_url = client.create_queue(QueueName="notification-queue")["QueueUrl"]
        yield QueueHarness(client, queue_url, session_factory)


def user_updated(user_id: uuid.UUID, version: int, phone: str) -> dict:
    return envelope(
        "UserUpdated",
        {
            "userId": str(user_id), "role": "GUARDIAN", "branchId": str(uuid.uuid4()), "firstName": "Fatma",
            "lastName": "Yılmaz", "email": None, "phone": phone, "active": True, "studentNumber": None,
            "recordVersion": version,
        },
    )  # fmt: skip


def test_user_updated_fills_the_replica_and_an_older_version_is_ignored(harness, db_session, caplog) -> None:
    user_id = uuid.uuid4()
    harness.send(user_updated(user_id, version=3, phone="05320000099"))
    assert harness.consumer.consume_batch() == 1
    harness.send(user_updated(user_id, version=2, phone="05320000001"))  # arrives late
    assert harness.consumer.consume_batch() == 1

    row = db_session.get(ContactReplica, user_id)
    assert (row.phone, row.record_version) == ("05320000099", 3)
    assert harness.messages_left() == 0
    assert any("contact_replica.update_ignored" in str(record.msg) for record in caplog.records)


def test_guardian_unlink_of_a_replaced_guardian_keeps_the_new_link(harness, db_session, caplog) -> None:
    student, old_guardian, new_guardian = uuid.uuid4(), uuid.uuid4(), uuid.uuid4()
    linked = {"guardianId": str(new_guardian), "studentId": str(student), "relationship": "FATHER"}
    harness.send(envelope("GuardianLinked", linked))
    harness.consumer.consume_batch()
    harness.send(envelope("GuardianUnlinked", {"guardianId": str(old_guardian), "studentId": str(student)}))
    harness.consumer.consume_batch()

    assert db_session.get(GuardianLinkReplica, student).guardian_id == new_guardian
    assert any("guardian_link.unlink_ignored" in str(record.msg) for record in caplog.records)


def test_exam_results_published_creates_the_notifications_of_the_drawing(
    harness, session_factory, db_session
) -> None:
    s = create_scenario(db_session)
    event = envelope(
        "ExamResultsPublished",
        {
            "mockExamId": str(uuid.uuid4()), "branchId": str(s.branch_id), "examTypeCode": "TYT",
            "studentIds": [str(s.ali), str(s.ayse)], "mockExamName": "TYT Deneme 3", "examDate": "2026-10-12",
        },
        source="exam-analysis",
    )  # fmt: skip
    harness.send(event)

    harness.consumer.consume_batch()

    rows = db_session.scalars(select(Notification)).all()
    by_person = {(row.recipient_id, row.channel): row for row in rows}
    assert set(by_person) == {
        (s.ali, Channel.IN_APP),  # no e-mail, no phone
        (s.ayse, Channel.IN_APP),
        (s.ayse, Channel.EMAIL),  # no phone
        (s.fatma, Channel.IN_APP),
        (s.fatma, Channel.EMAIL),
        (s.fatma, Channel.SMS),
        (s.hasan, Channel.IN_APP),  # no e-mail, SMS turned off
    }
    assert by_person[(s.ali, Channel.IN_APP)].status == DeliveryStatus.SENT
    assert by_person[(s.fatma, Channel.EMAIL)].status == DeliveryStatus.PENDING
    assert by_person[(s.fatma, Channel.EMAIL)].title == "Ali Yılmaz: TYT Deneme 3 sonucu açıklandı"
    assert harness.messages_left() == 0

    # The same event delivered again (at-least-once) changes nothing.
    harness.send(event)
    harness.consumer.consume_batch()
    with session_factory() as session:
        assert session.scalar(select(func.count()).select_from(Notification)) == 7
        assert session.scalar(select(func.count()).select_from(ProcessedEvent)) == 1
    assert harness.messages_left() == 0


def test_invalid_messages_stay_in_the_queue_for_the_dlq(harness, db_session, caplog) -> None:
    harness.send("not json")
    harness.send(envelope("GuardianLinked", {"guardianId": "x"}))  # envelope fine, data invalid

    harness.consumer.consume_batch()

    assert harness.messages_left() == 2
    assert db_session.scalar(select(func.count()).select_from(ProcessedEvent)) == 0
    events = {str(record.msg) for record in caplog.records}
    assert any("event.invalid_envelope" in event for event in events)
    assert any("event.invalid_data" in event for event in events)


def test_unknown_event_type_is_logged_and_deleted(harness, caplog) -> None:
    harness.send(envelope("StudentAddedToClassroom", {}))

    harness.consumer.consume_batch()

    assert harness.messages_left() == 0
    assert any("event.unknown_type_skipped" in str(record.msg) for record in caplog.records)


def test_worker_finds_the_queue_by_name_or_logs_that_consuming_is_off(session_factory, caplog) -> None:
    from app.config import Settings
    from app.worker.__main__ import build_consumer

    with mock_aws():
        boto3.client("sqs", region_name="eu-central-1").create_queue(QueueName="notification-queue")

        assert build_consumer(Settings(sqs_queue_name="notification-queue")) is not None
        assert build_consumer(Settings(sqs_queue_name="")) is None

    assert any("consumer.disabled" in str(record.msg) for record in caplog.records)
