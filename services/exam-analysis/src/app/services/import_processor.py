"""Worker-side processing of optical import jobs (one job = one transaction)."""

import time
import uuid
from dataclasses import dataclass, field
from datetime import timedelta

import structlog
from fastapi.encoders import jsonable_encoder
from sqlalchemy.orm import Session, sessionmaker

from app.cache import ResultCache
from app.errors import BusinessRuleError, ConflictError, DomainError
from app.models.base import utcnow
from app.models.enums import ExamStatus, JobStatus
from app.observability.metrics import (
    IMPORT_JOB_DURATION,
    IMPORT_JOB_QUEUE_WAIT,
    IMPORT_JOBS,
    IMPORT_JOBS_REQUEUED,
    IMPORT_ROWS,
)
from app.repositories import catalog as catalog_repo
from app.repositories import exams as exams_repo
from app.repositories import imports as imports_repo
from app.repositories import replicas as replicas_repo
from app.repositories import results as results_repo
from app.schemas.optical import OpticalFieldDefinition
from app.scoring.optical_parser import decode_lines, parse_lines
from app.scoring.row_matching import classify_rows
from app.services import result_writer, scoring_service
from app.services.import_job_service import IMPORTABLE_STATUSES
from app.services.sheet_scoring import score_sheets
from app.storage.file_storage import FileStorage

logger = structlog.get_logger(__name__)

UNEXPECTED_ERROR_MESSAGE = "Unexpected error while processing the file; see the service logs for this job id"


@dataclass(frozen=True) # deger atandıktan sonra degistirilemez demek
class ClaimedJob:
    """A job this worker took from the queue."""
    job_id: uuid.UUID
    mock_exam_id: uuid.UUID
    queue_wait_seconds: float


@dataclass
class ImportSummary:
    """Counts and ids of one processed file, used for metrics, logs and cache invalidation."""
    mock_exam_id: uuid.UUID
    exam_type_id: uuid.UUID
    total_rows: int
    matched_rows: int
    unmatched_by_reason: dict[str, int]
    blank_lines_skipped: int
    short_lines_padded: int
    invalid_sheet_refs: int
    student_ids: list[uuid.UUID] = field(default_factory=list)


class ImportProcessor:
    """Worker steps: take the next queued job, process it, and requeue jobs of crashed workers."""

    def __init__( self, session_factory: sessionmaker[Session], storage: FileStorage, cache: ResultCache ) -> None:
        self._session_factory = session_factory
        self._storage = storage
        self._cache = cache

    def process_next(self) -> bool:
        """Claims and processes the oldest queued job; returns False when the queue is empty."""
        claimed = self._claim()
        if claimed is None:
            logger.debug("import_job.queue_empty")
            return False
        self._process(claimed)
        return True
    # is basladı yarıda kaldı onun icin kullanılıyor
    def requeue_stale(self, timeout_seconds: int) -> int:
        """Puts PROCESSING jobs that started more than timeout_seconds ago back to QUEUED.

        Such a job's worker died; its transaction was rolled back, so the job is safe to run again.
        Returns the number of requeued jobs.
        """
        with self._session_factory() as session:
            job_ids = imports_repo.requeue_stale_jobs(session, utcnow() - timedelta(seconds=timeout_seconds)) #durum burada degisiyor
            session.commit()
        for job_id in job_ids:
            IMPORT_JOBS_REQUEUED.inc()
            logger.warning(
                "import_job.requeued",
                import_job_id=str(job_id),
                reason="processing_timeout",
                timeout_seconds=timeout_seconds,
            )
        return len(job_ids)

    def _claim(self) -> ClaimedJob | None:
        """Takes the oldest QUEUED job and marks it PROCESSING.

        FOR UPDATE SKIP LOCKED makes sure two workers never take the same job. Returns None when the
        queue is empty.
        """
        with self._session_factory() as session: #yeni bir db bağlantısı açıyor with bitice bağlantı kapanıyor
            job = imports_repo.claim_next_job(session)
            if job is None:
                return None
            now = utcnow()
            job.status = JobStatus.PROCESSING
            job.started_at = now
            claimed = ClaimedJob(job.id, job.mock_exam_id, (now - job.created_at).total_seconds())
            session.commit()
        IMPORT_JOB_QUEUE_WAIT.observe(claimed.queue_wait_seconds)
        logger.info(
            "import_job.claimed",
            import_job_id=str(claimed.job_id),
            mock_exam_id=str(claimed.mock_exam_id),
            queue_wait_seconds=round(claimed.queue_wait_seconds, 3),
        )
        return claimed

    def _process(self, claimed: ClaimedJob) -> None:
        """Runs run_import in its own transaction.

        On success records metrics and clears the cache; on any error the transaction is rolled back and
        the job is marked FAILED with a message.
        """
        started = time.perf_counter() #process ne kadar süreck diye timer
        with structlog.contextvars.bound_contextvars(
            import_job_id=str(claimed.job_id), mock_exam_id=str(claimed.mock_exam_id)
        ):
            try:
                with self._session_factory() as session:
                    summary = run_import(session, self._storage, claimed.job_id)
                    session.commit()
            except DomainError as exc:
                self._mark_failed(claimed.job_id, exc.message)
                logger.warning(
                    "import_job.failed",
                    code=exc.code,
                    reason=exc.message,
                    context=jsonable_encoder(exc.context),
                )
                IMPORT_JOBS.labels(status=JobStatus.FAILED.value).inc()
                return
            except Exception:
                self._mark_failed(claimed.job_id, UNEXPECTED_ERROR_MESSAGE)
                logger.exception("import_job.failed", code="UNEXPECTED_ERROR")
                IMPORT_JOBS.labels(status=JobStatus.FAILED.value).inc()
                return

            duration = time.perf_counter() - started
            IMPORT_JOB_DURATION.observe(duration)
            IMPORT_JOBS.labels(status=JobStatus.COMPLETED.value).inc()
            IMPORT_ROWS.labels(result="matched", reason="").inc(summary.matched_rows)
            for reason, count in summary.unmatched_by_reason.items():
                IMPORT_ROWS.labels(result="unmatched", reason=reason).inc(count)
            if summary.invalid_sheet_refs:
                logger.warning(
                    "import_job.invalid_sheet_refs",
                    rows=summary.invalid_sheet_refs,
                    reason="sheet_ref is not a file id; the sheet image link is left empty",
                )
            self._cache.invalidate_exam(summary.mock_exam_id, summary.exam_type_id, summary.student_ids)
            logger.info(
                "import_job.completed",
                total_rows=summary.total_rows,
                matched_rows=summary.matched_rows,
                unmatched_rows=sum(summary.unmatched_by_reason.values()),
                unmatched_by_reason=summary.unmatched_by_reason,
                blank_lines_skipped=summary.blank_lines_skipped,
                short_lines_padded=summary.short_lines_padded,
                duration_seconds=round(duration, 3),
            )

    def _mark_failed(self, job_id: uuid.UUID, message: str) -> None:
        """Marks a job FAILED in a separate transaction, so the status survives the rolled-back import."""
        with self._session_factory() as session:
            job = imports_repo.get_import_job(session, job_id)
            job.status = JobStatus.FAILED
            job.error_message = message
            job.finished_at = utcnow()
            session.commit()


def run_import(session: Session, storage: FileStorage, job_id: uuid.UUID) -> ImportSummary:
    """Processes one optical file end to end.

    Parses the lines, matches student numbers, grades the matched rows, writes results and unmatched
    rows, recomputes statistics, scores and ranks, then marks the job COMPLETED and the exam
    PROCESSED. Raises a domain error when the file cannot be processed. Does not commit.
    """
    job = imports_repo.get_import_job(session, job_id)
    exam = exams_repo.get_mock_exam(session, job.mock_exam_id, for_update=True)
    if exam.status not in IMPORTABLE_STATUSES:
        raise ConflictError(
            "EXAM_NOT_IMPORTABLE",
            f"Exam status {exam.status.value} does not accept optical files",
            status=exam.status.value,
        )
    definition = OpticalFieldDefinition.model_validate(
        catalog_repo.get_optical_format(session, job.optical_format_id).field_definition
    )
    answer_key = exams_repo.answer_key_frame(session, exam.id) #bu bir data frame olarka gelecek
    if answer_key.empty:
        raise ConflictError("ANSWER_KEY_EMPTY", "The exam has no answer key")
    key_questions = int(answer_key["question_no"].max())
    if key_questions > definition.question_count:
        raise BusinessRuleError(
            "ANSWER_KEY_EXCEEDS_FORMAT",
            f"The key has {key_questions} questions but the optical format reads {definition.question_count}",
            key_questions=key_questions,
            format_questions=definition.question_count,
        )

    content = storage.get(job.file_id)
    try:
        lines = decode_lines(content, definition.encoding)
    except UnicodeDecodeError as exc:
        raise BusinessRuleError(
            "FILE_ENCODING_MISMATCH",
            f"The file is not valid {definition.encoding}",
            encoding=definition.encoding,
        ) from exc
    parsed = parse_lines(lines, definition)
    if parsed.sheets.empty:
        raise BusinessRuleError("FILE_EMPTY", "The file has no data lines")

    classification = classify_rows(
        parsed.sheets,
        valid_booklets=set(answer_key["booklet"]),
        student_ids_by_no=replicas_repo.student_ids_by_no(session, exam.branch_id),
        existing_student_ids=results_repo.existing_student_ids(session, exam.id),
    )
    matched = classification.matched
    if not matched.empty:
        exam_type = catalog_repo.get_exam_type(session, exam.exam_type_id)
        scored = score_sheets(matched, answer_key, definition, float(exam_type.wrong_divisor))
        result_writer.insert_results(session, mock_exam_id=exam.id, matched=matched, scored=scored)
    unmatched_outcome = result_writer.insert_unmatched_rows(session, job.id, classification.unmatched)
    session.flush()
    scoring_service.recalculate_exam(session, exam, refresh_institutional=True)

    job.total_rows = len(parsed.sheets)
    job.succeeded_rows = len(matched)
    job.failed_rows = len(classification.unmatched)
    job.status = JobStatus.COMPLETED
    job.finished_at = utcnow()
    if exam.status == ExamStatus.KEY_READY:
        exam.status = ExamStatus.PROCESSED

    return ImportSummary(
        mock_exam_id=exam.id,
        exam_type_id=exam.exam_type_id,
        total_rows=len(parsed.sheets),
        matched_rows=len(matched),
        unmatched_by_reason={
            str(reason): int(count)
            for reason, count in classification.unmatched["reason"].value_counts().items()
        },
        blank_lines_skipped=parsed.blank_lines_skipped,
        short_lines_padded=parsed.short_lines_padded,
        invalid_sheet_refs=unmatched_outcome.invalid_sheet_refs,
        student_ids=matched["student_id"].tolist(),
    )
