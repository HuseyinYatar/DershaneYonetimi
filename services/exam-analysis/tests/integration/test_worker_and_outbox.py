import uuid
from datetime import date, timedelta

import boto3
import pytest
from alembic.autogenerate import compare_metadata
from alembic.migration import MigrationContext
from moto import mock_aws
from sqlalchemy import select

from app.db import get_engine
from app.events.outbox import add_event
from app.events.payloads import StudentResultAdded
from app.events.publisher import InMemoryEventPublisher, SnsEventPublisher
from app.events.relay import OutboxRelay
from app.models import Base, ImportJob, MockExam, OutboxEvent
from app.models.base import utcnow
from app.models.enums import ExamStatus, ImportSource, JobStatus, ScoringSource
from app.repositories import imports as imports_repo

pytestmark = pytest.mark.integration


def test_migrations_match_the_models(migrated_database: str) -> None:
    with get_engine().connect() as connection:
        diff = compare_metadata(
            MigrationContext.configure(connection, opts={"compare_type": True}), Base.metadata
        )

    assert diff == []


def make_exam(session, catalog, status: ExamStatus = ExamStatus.KEY_READY) -> MockExam:
    exam = MockExam(
        exam_type_id=catalog.exam_type_id,
        name="Deneme",
        exam_date=date(2026, 10, 10),
        branch_id=uuid.uuid4(),
        scoring_source=ScoringSource.INSTITUTIONAL,
        status=status,
        created_by=uuid.uuid4(),
    )
    session.add(exam)
    session.flush()
    return exam


def make_job(session, exam: MockExam, catalog, file_id: uuid.UUID | None = None) -> ImportJob:
    job = ImportJob(
        mock_exam_id=exam.id,
        optical_format_id=catalog.optical_format_id,
        file_id=file_id or uuid.uuid4(),
        source=ImportSource.FILE_UPLOAD,
        requested_by=uuid.uuid4(),
    )
    session.add(job)
    session.commit()
    return job


def test_concurrent_workers_never_claim_the_same_job(session_factory, db_session, catalog) -> None:
    exam = make_exam(db_session, catalog)
    first, second = make_job(db_session, exam, catalog), make_job(db_session, exam, catalog)

    with session_factory() as worker_a, session_factory() as worker_b, session_factory() as worker_c:
        claimed_a = imports_repo.claim_next_job(worker_a)
        claimed_b = imports_repo.claim_next_job(worker_b)  # worker_a still holds its row lock
        third = imports_repo.claim_next_job(worker_c)

        assert {claimed_a.id, claimed_b.id} == {first.id, second.id}
        assert third is None


def test_stale_processing_jobs_are_requeued(processor, db_session, catalog, caplog) -> None:
    exam = make_exam(db_session, catalog)
    job = make_job(db_session, exam, catalog)
    job.status = JobStatus.PROCESSING
    job.started_at = utcnow() - timedelta(hours=1)
    db_session.commit()

    assert processor.requeue_stale(timeout_seconds=900) == 1

    db_session.refresh(job)
    assert (job.status, job.started_at) == (JobStatus.QUEUED, None)
    assert any("import_job.requeued" in str(record.msg) for record in caplog.records)


@pytest.mark.parametrize(
    ("content", "expected_message"),
    [
        ("ST7001AĞÜŞ".encode("windows-1254"), "The file is not valid utf-8"),
        (b"\n\n", "The file has no data lines"),
    ],
)
def test_unprocessable_files_fail_the_job_with_a_reason(
    processor, storage, db_session, catalog, content: bytes, expected_message: str
) -> None:
    from app.models import AnswerKeyItem

    exam = make_exam(db_session, catalog)
    db_session.add(
        AnswerKeyItem(
            mock_exam_id=exam.id,
            subject_id=catalog.math_id,
            topic_id=catalog.numbers_id,
            booklet="A",
            question_no=1,
            correct_answer="A",
        )
    )
    file_id = uuid.uuid4()
    storage.put(file_id, content, "text/plain")
    job = make_job(db_session, exam, catalog, file_id)

    processor.process_next()

    db_session.refresh(job)
    assert job.status == JobStatus.FAILED
    assert job.error_message == expected_message
    assert job.finished_at is not None


def test_job_for_a_published_exam_fails(processor, db_session, catalog) -> None:
    exam = make_exam(db_session, catalog, status=ExamStatus.PUBLISHED)
    job = make_job(db_session, exam, catalog)

    processor.process_next()

    db_session.refresh(job)
    assert job.status == JobStatus.FAILED
    assert "PUBLISHED" in job.error_message


def stage_event(session_factory) -> uuid.UUID:
    with session_factory() as session:
        event_id = add_event(
            session,
            StudentResultAdded(mock_exam_id=uuid.uuid4(), branch_id=uuid.uuid4(), student_id=uuid.uuid4()),
        )
        session.commit()
    return event_id


def test_relay_marks_events_published(session_factory) -> None:
    event_id = stage_event(session_factory)
    publisher = InMemoryEventPublisher()

    assert OutboxRelay(session_factory, publisher, batch_size=10).relay_batch() == 1
    assert OutboxRelay(session_factory, publisher, batch_size=10).relay_batch() == 0

    assert [envelope["eventId"] for envelope in publisher.published] == [str(event_id)]
    with session_factory() as session:
        assert session.get(OutboxEvent, event_id).published_at is not None


def test_failed_publish_keeps_the_event_and_records_the_error(session_factory, mocker, caplog) -> None:
    event_id = stage_event(session_factory)
    publisher = mocker.Mock()
    publisher.publish.side_effect = ConnectionError("broker down")

    assert OutboxRelay(session_factory, publisher, batch_size=10).relay_batch() == 0

    with session_factory() as session:
        event = session.get(OutboxEvent, event_id)
        assert event.published_at is None
        assert event.attempts == 1
        assert event.last_error == "ConnectionError: broker down"
    assert any("outbox.publish_failed" in str(record.msg) for record in caplog.records)


@mock_aws
def test_relay_delivers_to_sqs_through_sns(session_factory, monkeypatch) -> None:
    monkeypatch.setenv("AWS_ACCESS_KEY_ID", "test")
    monkeypatch.setenv("AWS_SECRET_ACCESS_KEY", "test")
    region = "eu-central-1"
    sns, sqs = boto3.client("sns", region_name=region), boto3.client("sqs", region_name=region)
    topic_arn = sns.create_topic(Name="dershane-events")["TopicArn"]
    queue_url = sqs.create_queue(QueueName="notification")["QueueUrl"]
    queue_arn = sqs.get_queue_attributes(QueueUrl=queue_url, AttributeNames=["QueueArn"])["Attributes"][
        "QueueArn"
    ]
    sns.subscribe(
        TopicArn=topic_arn,
        Protocol="sqs",
        Endpoint=queue_arn,
        Attributes={"RawMessageDelivery": "true", "FilterPolicy": '{"eventType": ["StudentResultAdded"]}'},
    )
    event_id = stage_event(session_factory)

    OutboxRelay(session_factory, SnsEventPublisher(sns, topic_arn), batch_size=10).relay_batch()

    messages = sqs.receive_message(QueueUrl=queue_url)["Messages"]
    assert str(event_id) in messages[0]["Body"]
    with session_factory() as session:
        assert session.scalar(select(OutboxEvent.published_at).where(OutboxEvent.id == event_id)) is not None
