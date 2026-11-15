import json
import re
import uuid
from datetime import UTC, datetime, timedelta

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
from app.models import (
    Base,
    ContactReplica,
    GuardianLinkReplica,
    Notification,
    NotificationPreference,
    ProcessedEvent,
)
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


def notifications_by_person(db_session) -> dict:
    return {(row.recipient_id, row.channel): row for row in db_session.scalars(select(Notification)).all()}


def attendance_event(event_type: str, student_id: uuid.UUID) -> dict:
    return envelope(
        event_type,
        {
            "recordId": str(uuid.uuid4()), "sessionId": str(uuid.uuid4()), "studentId": str(student_id),
            "classroomId": str(uuid.uuid4()), "branchId": str(uuid.uuid4()), "subjectId": str(uuid.uuid4()),
            "sessionDate": "2026-11-14", "startTime": "10:00:00", "endTime": "10:40:00",
        },
        source="attendance",
    )  # fmt: skip


def task_event(event_type: str, student_id: uuid.UUID, due_at: datetime, **extra: object) -> dict:
    return envelope(
        event_type,
        {
            "assignmentId": str(uuid.uuid4()), "taskId": str(uuid.uuid4()), "studentId": str(student_id),
            "branchId": str(uuid.uuid4()), "title": "Paragraf testi",
            "dueAt": due_at.astimezone(UTC).strftime("%Y-%m-%dT%H:%M:%SZ"), **extra,
        },
        source="task",
    )  # fmt: skip


def test_student_absent_reaches_the_student_and_the_guardian_on_every_channel(harness, db_session) -> None:
    s = create_scenario(db_session)
    harness.send(attendance_event("StudentAbsent", s.ali))

    harness.consumer.consume_batch()

    rows = notifications_by_person(db_session)
    assert set(rows) == {
        (s.ali, Channel.IN_APP),  # Ali has no e-mail and no phone
        (s.fatma, Channel.IN_APP),
        (s.fatma, Channel.EMAIL),
        (s.fatma, Channel.SMS),
    }
    assert rows[(s.fatma, Channel.EMAIL)].title == "Ali Yılmaz: 14 Kasım 2026 10:00 dersinde devamsız"
    assert rows[(s.fatma, Channel.SMS)].body == (
        f"Ali Yılmaz 14 Kasım 2026 10:00–10:40 dersinde devamsız: http://fe/students/{s.ali}/attendance"
    )
    assert harness.messages_left() == 0


def test_teacher_excuse_follows_the_attendance_preference(harness, db_session) -> None:
    s = create_scenario(db_session)
    # Fatma turned SMS off for attendance; the switch is stored under StudentAbsent.
    db_session.add(NotificationPreference(user_id=s.fatma, event_type="StudentAbsent", channel=Channel.SMS))
    db_session.commit()
    harness.send(attendance_event("StudentExcusedByTeacher", s.ali))

    harness.consumer.consume_batch()

    rows = notifications_by_person(db_session)
    assert set(rows) == {(s.ali, Channel.IN_APP), (s.fatma, Channel.IN_APP), (s.fatma, Channel.EMAIL)}
    assert "öğretmeni tarafından izinli sayıldı" in rows[(s.fatma, Channel.EMAIL)].body


def test_task_assigned_goes_only_to_the_student_and_never_by_sms(harness, db_session) -> None:
    s = create_scenario(db_session)
    db_session.get(ContactReplica, s.ayse).phone = "05350000003"
    db_session.commit()
    due_at = datetime(2026, 11, 16, 20, 59, tzinfo=UTC)
    harness.send(task_event("TaskAssigned", s.ayse, due_at, recurrence="ONCE"))

    harness.consumer.consume_batch()

    rows = notifications_by_person(db_session)
    assert set(rows) == {(s.ayse, Channel.IN_APP), (s.ayse, Channel.EMAIL)}  # no Hasan, no SMS
    assert rows[(s.ayse, Channel.EMAIL)].title == "Yeni ödev: Paragraf testi"
    assert "Son teslim: 16 Kasım 2026 23:59" in rows[(s.ayse, Channel.EMAIL)].body  # UTC -> Istanbul


def test_task_overdue_also_reaches_the_guardian(harness, db_session) -> None:
    s = create_scenario(db_session)
    harness.send(task_event("TaskOverdue", s.ali, datetime(2026, 11, 16, 20, 59, tzinfo=UTC)))

    harness.consumer.consume_batch()

    rows = notifications_by_person(db_session)
    assert set(rows) == {(s.ali, Channel.IN_APP), (s.fatma, Channel.IN_APP), (s.fatma, Channel.EMAIL)}
    assert rows[(s.fatma, Channel.IN_APP)].title == "Ali Yılmaz: ödev zamanında teslim edilmedi"


def test_submission_graded_text_has_no_score(harness, db_session) -> None:
    s = create_scenario(db_session)
    harness.send(task_event("SubmissionGraded", s.ayse, datetime.now(UTC), score=85))

    harness.consumer.consume_batch()

    rows = notifications_by_person(db_session)
    assert set(rows) == {(s.ayse, Channel.IN_APP), (s.ayse, Channel.EMAIL)}
    # Links carry random UUIDs, which may contain "85" themselves: check the text without them.
    texts = [re.sub(r"https?://\S+", "", row.title + row.body) for row in rows.values()]
    assert all("85" not in text for text in texts)


def test_a_reminder_read_after_the_deadline_is_skipped(harness, db_session, caplog) -> None:
    s = create_scenario(db_session)
    harness.send(task_event("TaskDueSoon", s.ayse, datetime.now(UTC) - timedelta(minutes=5)))
    harness.consumer.consume_batch()

    assert notifications_by_person(db_session) == {}
    assert db_session.scalar(select(func.count()).select_from(ProcessedEvent)) == 1  # handled, not retried
    assert harness.messages_left() == 0
    assert any("notification.event_skipped" in str(record.msg) for record in caplog.records)

    harness.send(task_event("TaskDueSoon", s.ayse, datetime.now(UTC) + timedelta(hours=2)))
    harness.consumer.consume_batch()

    assert set(notifications_by_person(db_session)) == {(s.ayse, Channel.IN_APP), (s.ayse, Channel.EMAIL)}


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
