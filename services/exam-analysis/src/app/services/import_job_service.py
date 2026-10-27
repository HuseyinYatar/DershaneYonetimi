import uuid

import structlog
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.auth.keycloak import CurrentUser
from app.errors import BusinessRuleError, ConflictError
from app.models import ImportJob, MockExam, UnmatchedRow
from app.models.enums import ExamStatus, ImportSource, JobStatus, MatchStatus
from app.repositories import catalog as catalog_repo
from app.repositories import imports as imports_repo
from app.services import mock_exam_service
from app.storage.file_storage import FileStorage

logger = structlog.get_logger(__name__)

IMPORTABLE_STATUSES = {ExamStatus.KEY_READY, ExamStatus.PROCESSED}


def ensure_importable(exam: MockExam) -> None:
    """Raises unless the exam accepts an optical file: key uploaded and results not yet published."""
    if exam.status == ExamStatus.DRAFT:
        raise ConflictError("ANSWER_KEY_NOT_READY", "Upload the answer key first", mock_exam_id=str(exam.id))
    if exam.status == ExamStatus.PUBLISHED:
        raise ConflictError(
            "EXAM_ALREADY_PUBLISHED",
            "Results are published; add missing students by matching unmatched rows",
            mock_exam_id=str(exam.id),
        )


def upload_and_create_job(
    session: Session,
    storage: FileStorage,
    user: CurrentUser,
    *,
    mock_exam_id: uuid.UUID,
    optical_format_id: uuid.UUID,
    content: bytes,
    content_type: str,
    max_upload_bytes: int,
) -> ImportJob:
    """Stores an uploaded optical file and queues an import job for the worker.

    Checks branch access, exam status, the optical format and the file size first. Temporary upload
    path until the file service exists; OMR imports call create_job directly.
    """
    exam = mock_exam_service.get_for_staff(session, user, mock_exam_id)
    ensure_importable(exam)
    catalog_repo.get_optical_format(session, optical_format_id)
    if not content:
        raise BusinessRuleError("FILE_EMPTY", "The uploaded file is empty")
    if len(content) > max_upload_bytes:
        raise BusinessRuleError(
            "FILE_TOO_LARGE",
            "The uploaded file exceeds the size limit",
            size=len(content),
            limit=max_upload_bytes,
        )
    file_id = uuid.uuid4()
    storage.put(file_id, content, content_type)
    logger.info("import_job.file_stored", file_id=str(file_id), size=len(content), mock_exam_id=str(exam.id))
    return create_job(
        session,
        exam=exam,
        optical_format_id=optical_format_id,
        file_id=file_id,
        source=ImportSource.FILE_UPLOAD,
        source_ref=None,
        requested_by=user.id,
    )


def create_job(
    session: Session,
    *,
    exam: MockExam,
    optical_format_id: uuid.UUID,
    file_id: uuid.UUID,
    source: ImportSource,
    source_ref: str | None,
    requested_by: uuid.UUID,
) -> ImportJob:
    """Inserts a QUEUED import job; the worker takes it from the table.

    Shared by file uploads and (later) OMR imports.
    """
    job = ImportJob(
        mock_exam_id=exam.id,
        optical_format_id=optical_format_id,
        file_id=file_id,
        source=source,
        source_ref=source_ref,
        status=JobStatus.QUEUED,
        requested_by=requested_by,
    )
    session.add(job)
    session.commit()
    logger.info(
        "import_job.queued",
        import_job_id=str(job.id),
        mock_exam_id=str(exam.id),
        source=source.value,
        requested_by=str(requested_by),
    )
    return job


def get_job(session: Session, user: CurrentUser, job_id: uuid.UUID) -> ImportJob:
    """Returns an import job (status, row counts, error message) to staff of the exam's branch."""
    job = imports_repo.get_import_job(session, job_id)
    mock_exam_service.get_for_staff(session, user, job.mock_exam_id)
    return job


def list_unmatched_rows(
    session: Session, user: CurrentUser, job_id: uuid.UUID, status: MatchStatus | None
) -> list[UnmatchedRow]:
    """Returns the rows of a job that matched no student, in file order, optionally filtered by status."""
    job = get_job(session, user, job_id)
    statement = select(UnmatchedRow).where(UnmatchedRow.import_job_id == job.id)
    if status is not None:
        statement = statement.where(UnmatchedRow.status == status)
    return list(session.scalars(statement.order_by(UnmatchedRow.line_no)))
