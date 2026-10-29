import uuid
from datetime import datetime

from sqlalchemy import select, update
from sqlalchemy.orm import Session

from app.errors import NotFoundError
from app.models import ImportJob, UnmatchedRow
from app.models.enums import JobStatus


def get_import_job(session: Session, job_id: uuid.UUID) -> ImportJob:
    job = session.get(ImportJob, job_id)
    if job is None:
        raise NotFoundError("IMPORT_JOB_NOT_FOUND", "Import job not found", import_job_id=str(job_id))
    return job


def get_unmatched_row(session: Session, row_id: uuid.UUID, *, for_update: bool = False) -> UnmatchedRow:
    statement = select(UnmatchedRow).where(UnmatchedRow.id == row_id)
    if for_update:
        statement = statement.with_for_update()
    row = session.scalars(statement).one_or_none()
    if row is None:
        raise NotFoundError(
            "UNMATCHED_ROW_NOT_FOUND", "Unmatched row not found", unmatched_row_id=str(row_id)
        )
    return row


def claim_next_job(session: Session) -> ImportJob | None:
    """Oldest queued job, locked so that concurrent workers skip it instead of waiting."""
    return session.scalars(
        select(ImportJob)
        .where(ImportJob.status == JobStatus.QUEUED)
        .order_by(ImportJob.created_at)
        .limit(1)
        .with_for_update(skip_locked=True)
    ).one_or_none()


def requeue_stale_jobs(session: Session, started_before: datetime) -> list[uuid.UUID]:
    """PROCESSING jobs whose worker stopped; their transaction was rolled back, so a retry is safe."""
    return list(
        session.scalars(
            update(ImportJob)
            .where(ImportJob.status == JobStatus.PROCESSING, ImportJob.started_at < started_before)
            .values(status=JobStatus.QUEUED, started_at=None)
            .returning(ImportJob.id)
        )
    )
