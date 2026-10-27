import uuid

import structlog
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.auth.access import ensure_branch_staff
from app.auth.keycloak import CurrentUser
from app.cache import ResultCache
from app.errors import ConflictError
from app.events.outbox import add_event
from app.events.payloads import ExamResultsPublished
from app.models import MockExam
from app.models.enums import ExamStatus
from app.repositories import catalog as catalog_repo
from app.repositories import exams as exams_repo
from app.repositories import results as results_repo
from app.schemas.exams import MockExamCreate
from app.services import weak_topic_service

logger = structlog.get_logger(__name__)

# Burada exam create etmek icin rol yetkilendirmesi isteniyor da buna gerek var mı emin değilim
# ilerde exam i direkt bir tablodan çekmek veya görüntü isleme ile alma ihtimalimiz olabilir
def create_mock_exam(session: Session, user: CurrentUser, request: MockExamCreate) -> MockExam:
    """Creates a mock exam in DRAFT status for the user's branch; the answer key comes next."""
    ensure_branch_staff(user, request.branch_id)
    catalog_repo.get_exam_type(session, request.exam_type_id)
    exam = MockExam(
        exam_type_id=request.exam_type_id,
        name=request.name,
        publisher=request.publisher,
        exam_date=request.exam_date,
        branch_id=request.branch_id,
        scoring_source=request.scoring_source,
        status=ExamStatus.DRAFT,
        created_by=user.id,
    )
    session.add(exam)
    session.commit()
    logger.info(
        "mock_exam.created",
        mock_exam_id=str(exam.id),
        branch_id=str(exam.branch_id),
        scoring_source=exam.scoring_source.value,
    )
    return exam


def list_mock_exams( session: Session, user: CurrentUser, branch_id: uuid.UUID, status: ExamStatus | None) -> list[MockExam]:
    """Lists the exams of a branch, newest first, optionally filtered by status."""
    ensure_branch_staff(user, branch_id)
    statement = select(MockExam).where(MockExam.branch_id == branch_id)
    if status is not None:
        statement = statement.where(MockExam.status == status)
    return list(session.scalars(statement.order_by(MockExam.exam_date.desc(), MockExam.created_at.desc())))


def get_for_staff( session: Session, user: CurrentUser, mock_exam_id: uuid.UUID, *, for_update: bool = False) -> MockExam:
    """Loads an exam and checks that the user is a teacher or admin of its branch.

    for_update=True locks the exam row until commit; every operation that rewrites results uses it.
    """
    exam = exams_repo.get_mock_exam(session, mock_exam_id, for_update=for_update)
    ensure_branch_staff(user, exam.branch_id)
    return exam


def publish(session: Session, user: CurrentUser, mock_exam_id: uuid.UUID, cache: ResultCache) -> MockExam:
    """Announces the results of a PROCESSED exam.

    Sets PUBLISHED, writes ExamResultsPublished to the outbox, recomputes the students' weak topics
    and clears the cached reports.
    """
    exam = get_for_staff(session, user, mock_exam_id, for_update=True)
    if exam.status == ExamStatus.PUBLISHED:
        raise ConflictError(
            "EXAM_ALREADY_PUBLISHED", "Results are already published", mock_exam_id=str(exam.id)
        )
    if exam.status != ExamStatus.PROCESSED:
        raise ConflictError(
            "EXAM_NOT_PROCESSED",
            "Results can be published after an optical file is processed",
            mock_exam_id=str(exam.id),
            status=exam.status.value,
        )
    student_ids = sorted(results_repo.existing_student_ids(session, exam.id))
    if not student_ids:
        raise ConflictError(
            "EXAM_HAS_NO_RESULTS", "No optical row was matched to a student yet", mock_exam_id=str(exam.id)
        )

    exam.status = ExamStatus.PUBLISHED
    exam_type = catalog_repo.get_exam_type(session, exam.exam_type_id)
    add_event(
        session,
        ExamResultsPublished(
            mock_exam_id=exam.id,
            branch_id=exam.branch_id,
            exam_type_code=exam_type.code,
            student_ids=student_ids,
        ),
    )
    weak_topic_service.recompute_for_students(session, exam.branch_id, student_ids)
    session.commit()
    cache.invalidate_exam(exam.id, exam.exam_type_id, student_ids)
    logger.info(
        "mock_exam.published", mock_exam_id=str(exam.id), students=len(student_ids), published_by=str(user.id)
    )
    return exam
