import json
import logging
import threading
import uuid
from datetime import UTC, datetime

import boto3
import pytest
from moto import mock_aws
from sqlalchemy import func, select

from app.config import Settings
from app.events.consumer import EventConsumer
from app.events.handlers import EventHandlers
from app.events.queue import SqsQueue
from app.models import ProcessedEvent, StudentReplica
from app.repositories import replicas as replicas_repo
from app.worker import __main__ as worker

pytestmark = pytest.mark.integration

BRANCH = uuid.uuid4()
QUEUE_NAME = "exam-analysis-user-events"


def envelope(event_type: str, data: dict, event_id: uuid.UUID | None = None) -> dict:
    return {
        "eventId": str(event_id or uuid.uuid4()),
        "eventType": event_type,
        "schemaVersion": 1,
        "occurredAt": datetime.now(UTC).isoformat().replace("+00:00", "Z"),
        "source": "user",
        "data": data,
    }


def user_updated(
    user_id: uuid.UUID,
    *,
    version: int = 0,
    number: str | None = "ST7001",
    first_name: str = "Ali",
    role: str = "STUDENT",
    active: bool = True,
) -> dict:
    return envelope(
        "UserUpdated",
        {
            "userId": str(user_id),
            "role": role,
            "branchId": str(BRANCH),
            "firstName": first_name,
            "lastName": "Yılmaz",
            "email": None,
            "phone": None,
            "active": active,
            "studentNumber": number,
            "recordVersion": version,
        },
    )


def added(student: uuid.UUID, classroom: uuid.UUID, number: str = "ST7001") -> dict:
    return envelope(
        "StudentAddedToClassroom",
        {
            "membershipId": str(uuid.uuid4()),
            "studentId": str(student),
            "classroomId": str(classroom),
            "branchId": str(BRANCH),
            "studentNumber": number,
            "fullName": "Ali Yılmaz",
            "startDate": "2026-11-02",
        },
    )


def removed(student: uuid.UUID, classroom: uuid.UUID) -> dict:
    return envelope(
        "StudentRemovedFromClassroom",
        {
            "membershipId": str(uuid.uuid4()),
            "studentId": str(student),
            "classroomId": str(classroom),
            "branchId": str(BRANCH),
            "endDate": "2026-11-02",
        },
    )


class QueueHarness:
    """A moto SQS queue plus the consumer reading it. Each delivery is consumed before the next one,
    because a standard queue keeps no order and the tests decide the order themselves."""

    def __init__(self, client, queue_url: str, session_factory) -> None:
        self.client = client
        self.queue_url = queue_url
        self.consumer = EventConsumer(
            session_factory, SqsQueue(client, queue_url, max_messages=10, wait_seconds=0), EventHandlers()
        )

    def deliver(self, body: dict | str) -> None:
        text = body if isinstance(body, str) else json.dumps(body)
        self.client.send_message(QueueUrl=self.queue_url, MessageBody=text)
        assert self.consumer.consume_batch() == 1

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
        queue_url = client.create_queue(QueueName=QUEUE_NAME)["QueueUrl"]
        yield QueueHarness(client, queue_url, session_factory)


def logged(caplog, event: str) -> bool:
    return any(event in str(record.msg) for record in caplog.records)


def test_new_student_appears_without_a_classroom_and_the_message_is_deleted(harness, db_session) -> None:
    student = uuid.uuid4()

    harness.deliver(user_updated(student))

    row = db_session.get(StudentReplica, student)
    assert (row.student_no, row.full_name, row.class_id) == ("ST7001", "Ali Yılmaz", None)
    assert (row.is_active, row.record_version, row.branch_id) == (True, 0, BRANCH)
    assert harness.messages_left() == 0
    assert replicas_repo.student_ids_by_no(db_session, BRANCH).to_dict() == {"ST7001": student}


def test_name_and_number_follow_updates_but_an_older_version_is_ignored(harness, db_session, caplog) -> None:
    student = uuid.uuid4()
    harness.deliver(user_updated(student, version=1))
    harness.deliver(user_updated(student, version=3, number="ST7099", first_name="Veli"))

    harness.deliver(user_updated(student, version=2, number="ST7055", first_name="Eski"))

    db_session.expire_all()
    row = db_session.get(StudentReplica, student)
    assert (row.student_no, row.full_name, row.record_version) == ("ST7099", "Veli Yılmaz", 3)
    assert logged(caplog, "student_replica.update_ignored")


def test_other_roles_are_not_copied(harness, db_session, caplog) -> None:
    with caplog.at_level(logging.DEBUG):
        harness.deliver(user_updated(uuid.uuid4(), role="GUARDIAN", number=None))

    assert db_session.scalar(select(func.count()).select_from(StudentReplica)) == 0
    assert harness.messages_left() == 0
    assert logged(caplog, "not_a_student")


def test_classroom_events_set_and_clear_the_classroom(harness, db_session, caplog) -> None:
    student, first_class, second_class = uuid.uuid4(), uuid.uuid4(), uuid.uuid4()
    harness.deliver(user_updated(student))
    harness.deliver(added(student, first_class))
    assert db_session.get(StudentReplica, student).class_id == first_class

    # Moved: the new classroom arrives first, the late removal from the old one must not clear it.
    harness.deliver(added(student, second_class))
    harness.deliver(removed(student, first_class))
    db_session.expire_all()
    assert db_session.get(StudentReplica, student).class_id == second_class
    assert logged(caplog, "student_replica.removal_ignored")

    harness.deliver(removed(student, second_class))
    db_session.expire_all()
    assert db_session.get(StudentReplica, student).class_id is None


def test_classroom_event_arriving_first_creates_the_row_and_user_updated_still_wins(
    harness, db_session
) -> None:
    student, classroom = uuid.uuid4(), uuid.uuid4()

    harness.deliver(added(student, classroom))

    row = db_session.get(StudentReplica, student)
    assert (row.class_id, row.record_version) == (classroom, None)

    harness.deliver(user_updated(student, version=0, first_name="Veli"))

    db_session.expire_all()
    row = db_session.get(StudentReplica, student)
    assert (row.full_name, row.record_version, row.class_id) == ("Veli Yılmaz", 0, classroom)


def test_a_departed_students_number_can_go_to_a_new_student(harness, db_session) -> None:
    departed, newcomer = uuid.uuid4(), uuid.uuid4()
    harness.deliver(user_updated(departed, version=0))
    harness.deliver(user_updated(departed, version=1, active=False))

    harness.deliver(user_updated(newcomer, version=0, first_name="Yeni"))

    db_session.expire_all()
    assert db_session.get(StudentReplica, departed).is_active is False
    assert replicas_repo.student_ids_by_no(db_session, BRANCH).to_dict() == {"ST7001": newcomer}


def test_same_event_delivered_twice_is_applied_once(harness, db_session, caplog) -> None:
    event = user_updated(uuid.uuid4())

    harness.deliver(event)
    harness.deliver(event)

    assert db_session.scalar(select(func.count()).select_from(ProcessedEvent)) == 1
    assert harness.messages_left() == 0
    assert logged(caplog, "event.duplicate_skipped")


def test_number_held_by_another_active_student_is_retried_not_lost(harness, db_session, caplog) -> None:
    harness.deliver(user_updated(uuid.uuid4()))

    harness.deliver(user_updated(uuid.uuid4()))  # same number; the holder's rename may still be on its way

    assert harness.messages_left() == 1
    assert db_session.scalar(select(func.count()).select_from(ProcessedEvent)) == 1
    assert logged(caplog, "event.failed")


def test_invalid_messages_stay_in_the_queue_for_the_dlq(harness, db_session, caplog) -> None:
    harness.deliver("not json")
    harness.deliver(envelope("UserUpdated", {"userId": "x"}))

    assert harness.messages_left() == 2
    assert db_session.scalar(select(func.count()).select_from(ProcessedEvent)) == 0
    assert logged(caplog, "event.invalid_envelope")
    assert logged(caplog, "event.invalid_data")


def test_unknown_event_type_is_logged_and_deleted(harness, caplog) -> None:
    harness.deliver(envelope("GuardianLinked", {}))

    assert harness.messages_left() == 0
    assert logged(caplog, "event.unknown_type_skipped")


def test_worker_loop_reads_the_queue_until_asked_to_stop(
    session_factory, db_session, tmp_path, mocker
) -> None:
    settings = Settings(
        worker_heartbeat_file=str(tmp_path / "heartbeat"), redis_url=None, sqs_queue_name=QUEUE_NAME
    )
    student = uuid.uuid4()
    stop = threading.Event()
    mocker.patch("app.worker.__main__.ImportProcessor.requeue_stale", return_value=0)
    mocker.patch("app.worker.__main__.ImportProcessor.process_next", return_value=False)
    original = EventConsumer.consume_batch

    def consume_once(self) -> int:
        received = original(self)
        stop.set()
        return received

    mocker.patch.object(EventConsumer, "consume_batch", consume_once)
    with mock_aws():
        client = boto3.client("sqs", region_name=settings.aws_region)
        queue_url = client.create_queue(QueueName=settings.sqs_queue_name)["QueueUrl"]
        client.send_message(QueueUrl=queue_url, MessageBody=json.dumps(user_updated(student)))

        worker.run(settings, stop)

    assert db_session.get(StudentReplica, student) is not None


def test_worker_logs_that_consuming_is_off_when_the_queue_name_is_empty(caplog) -> None:
    assert worker.build_consumer(Settings()) is None
    assert logged(caplog, "consumer.disabled")
