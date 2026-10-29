"""Manual resolution of unmatched optical rows by an admin."""

import uuid

import structlog
from sqlalchemy.orm import Session

from app.auth.keycloak import CurrentUser
from app.cache import ResultCache
from app.errors import BusinessRuleError, ConflictError, NotFoundError
from app.events.outbox import add_event
from app.events.payloads import StudentResultAdded
from app.models import StudentReplica, UnmatchedRow
from app.models.base import utcnow
from app.models.enums import ExamStatus, MatchStatus, UnmatchedReason
from app.repositories import catalog as catalog_repo
from app.repositories import exams as exams_repo
from app.repositories import imports as imports_repo
from app.repositories import results as results_repo
from app.schemas.optical import OpticalFieldDefinition
from app.scoring.optical_parser import parse_lines
from app.services import mock_exam_service, result_writer, scoring_service, weak_topic_service
from app.services.sheet_scoring import score_sheets

logger = structlog.get_logger(__name__)

# Malformed lines and unknown booklets cannot be scored no matter which student they belong to.
MATCHABLE_REASONS = {UnmatchedReason.STUDENT_NOT_FOUND, UnmatchedReason.DUPLICATE_STUDENT}
SCORED_STATUSES = {ExamStatus.PROCESSED, ExamStatus.PUBLISHED}


def match_row(  session: Session, user: CurrentUser, row_id: uuid.UUID, student_id: uuid.UUID, cache: ResultCache) -> UnmatchedRow:
    """Links an unmatched row to a student chosen by the admin and scores it.

    The student must be in the exam's branch and must not have a result yet. Statistics, scores and
    ranks of the whole exam are recalculated. If the exam is already published, only
    StudentResultAdded is published, for this student.
    """
    #souncu
    row = imports_repo.get_unmatched_row(session, row_id, for_update=True)
    # hangi denemeye ait oldugunu bulamk icin job satırını getirdik
    job = imports_repo.get_import_job(session, row.import_job_id)
    exam = mock_exam_service.get_for_staff(session, user, job.mock_exam_id, for_update=True)
    _ensure_pending(row)
    if row.reason not in MATCHABLE_REASONS:
        raise BusinessRuleError(
            "UNMATCHED_ROW_NOT_SCORABLE",
            "This row cannot be scored; mark it as ignored",
            unmatched_row_id=str(row.id),
            reason=row.reason.value,
        )
    if exam.status not in SCORED_STATUSES:
        raise ConflictError("EXAM_NOT_PROCESSED", "The exam has not been processed", status=exam.status.value)

    #burda replica tuttugumuz studenti bulamay çalışıyoruz
    replica = session.get(StudentReplica, student_id)
    if replica is None:
        raise NotFoundError(
            "STUDENT_NOT_FOUND", "Student is unknown to this service", student_id=str(student_id)
        )
    if replica.branch_id != exam.branch_id:
        raise BusinessRuleError(
            "STUDENT_NOT_IN_EXAM_BRANCH",
            "Only students of the exam's branch can be matched",
            student_id=str(student_id),
            branch_id=str(exam.branch_id),
        )
    if student_id in results_repo.existing_student_ids(session, exam.id):
        raise ConflictError(
            "STUDENT_ALREADY_HAS_RESULT",
            "The student already has a result in this exam",
            student_id=str(student_id),
            mock_exam_id=str(exam.id),
        )
    # denemenin okunacağı optik formatı döndürüyor
    definition = OpticalFieldDefinition.model_validate(
        catalog_repo.get_optical_format(session, job.optical_format_id).field_definition
    )
    # ilgili denemenin cevap anahtarlarını df olarak döndürüyor
    answer_key = exams_repo.answer_key_frame(session, exam.id)
    # bu kısımda optigin tanımına göre veriler df yapıp ger döndürüyor artık tanımsız olan ögrenci de bu df içinde
    sheets = parse_lines([row.raw_line], definition).sheets.assign(student_id=student_id)
    if not sheets["booklet"].isin(set(answer_key["booklet"])).all():
        raise BusinessRuleError(
            "INVALID_BOOKLET", "The row's booklet is not in the answer key", unmatched_row_id=str(row.id)
        )

    exam_type = catalog_repo.get_exam_type(session, exam.exam_type_id)

    scored = score_sheets(sheets, answer_key, definition, float(exam_type.wrong_divisor))
    result_writer.insert_results(session, mock_exam_id=exam.id, matched=sheets, scored=scored)

    row.status = MatchStatus.MATCHED
    row.matched_student_id = student_id
    row.resolved_by = user.id
    row.resolved_at = utcnow()
    session.flush()
    # A new participant changes the institutional mean and standard deviation, so every
    # student's estimated score and rank is recalculated.
    scoring_service.recalculate_exam(session, exam, refresh_institutional=True)

    published = exam.status == ExamStatus.PUBLISHED
    if published:
        # Results were already announced: notify only this student, never re-announce the exam.
        add_event(
            session, StudentResultAdded(mock_exam_id=exam.id, branch_id=exam.branch_id, student_id=student_id)
        )
        weak_topic_service.recompute_for_students(session, exam.branch_id, [student_id])
    session.commit()
    cache.invalidate_exam(exam.id, exam.exam_type_id, [student_id])
    logger.info(
        "unmatched_row.matched",
        unmatched_row_id=str(row.id),
        mock_exam_id=str(exam.id),
        student_id=str(student_id),
        exam_published=published,
        resolved_by=str(user.id),
    )
    return row


def ignore_row(session: Session, user: CurrentUser, row_id: uuid.UUID) -> UnmatchedRow:
    """Marks an unmatched row IGNORED (kept, not deleted) and records who resolved it."""
    row = imports_repo.get_unmatched_row(session, row_id, for_update=True)
    job = imports_repo.get_import_job(session, row.import_job_id)
    mock_exam_service.get_for_staff(session, user, job.mock_exam_id)
    _ensure_pending(row)
    row.status = MatchStatus.IGNORED
    row.resolved_by = user.id
    row.resolved_at = utcnow()
    session.commit()
    logger.info(
        "unmatched_row.ignored",
        unmatched_row_id=str(row.id),
        reason=row.reason.value,
        resolved_by=str(user.id),
    )
    return row


def _ensure_pending(row: UnmatchedRow) -> None:
    """Raises if the row was already matched or ignored."""
    if row.status != MatchStatus.PENDING:
        raise ConflictError(
            "UNMATCHED_ROW_ALREADY_RESOLVED",
            "The row has already been resolved",
            unmatched_row_id=str(row.id),
            status=row.status.value,
        )
