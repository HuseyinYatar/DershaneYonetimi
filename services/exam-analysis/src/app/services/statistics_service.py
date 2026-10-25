import uuid

import structlog
from sqlalchemy import delete, select
from sqlalchemy.orm import Session

from app.auth.keycloak import CurrentUser
from app.cache import ResultCache
from app.errors import BusinessRuleError
from app.models import ExamSubjectStatistic
from app.models.enums import ScoringSource
from app.repositories import catalog as catalog_repo
from app.repositories import results as results_repo
from app.schemas.results import NationalStatisticsUpdate
from app.services import mock_exam_service, scoring_service

logger = structlog.get_logger(__name__)


def list_statistics(
    session: Session, user: CurrentUser, mock_exam_id: uuid.UUID
) -> list[ExamSubjectStatistic]:
    """Returns the institutional and national subject statistics (mean, standard deviation) of an exam."""
    exam = mock_exam_service.get_for_staff(session, user, mock_exam_id)
    return list(
        session.scalars(
            select(ExamSubjectStatistic)
            .where(ExamSubjectStatistic.mock_exam_id == exam.id)
            .order_by(ExamSubjectStatistic.source, ExamSubjectStatistic.subject_id)
        )
    )


def set_national_statistics(
    session: Session,
    user: CurrentUser,
    mock_exam_id: uuid.UUID,
    request: NationalStatisticsUpdate,
    cache: ResultCache,
) -> list[ExamSubjectStatistic]:
    """Admins enter the publisher's nationwide mean and standard deviation per subject. When the
    exam is scored with NATIONAL statistics, estimated scores appear as soon as they are entered."""
    exam = mock_exam_service.get_for_staff(session, user, mock_exam_id, for_update=True)
    subject_ids = [item.subject_id for item in request.items]
    if len(set(subject_ids)) != len(subject_ids):
        raise BusinessRuleError("DUPLICATE_SUBJECT", "Each subject may appear once")
    foreign = set(subject_ids) - catalog_repo.subject_ids_of_exam_type(session, exam.exam_type_id)
    if foreign:
        raise BusinessRuleError(
            "SUBJECT_NOT_IN_EXAM_TYPE",
            "Statistics may only be entered for subjects of the exam's type",
            subject_ids=sorted(str(subject_id) for subject_id in foreign),
        )

    session.execute(
        delete(ExamSubjectStatistic).where(
            ExamSubjectStatistic.mock_exam_id == exam.id,
            ExamSubjectStatistic.source == ScoringSource.NATIONAL,
        )
    )
    session.add_all(
        ExamSubjectStatistic(
            mock_exam_id=exam.id,
            subject_id=item.subject_id,
            source=ScoringSource.NATIONAL,
            mean=item.mean,
            std_dev=item.std_dev,
            participant_count=item.participant_count,
            updated_by=user.id,
        )
        for item in request.items
    )
    session.flush()

    rescored = exam.scoring_source == ScoringSource.NATIONAL
    if rescored:
        scoring_service.recalculate_exam(session, exam, refresh_institutional=False)
    student_ids = sorted(results_repo.existing_student_ids(session, exam.id))
    session.commit()
    cache.invalidate_exam(exam.id, exam.exam_type_id, student_ids)
    logger.info(
        "statistics.national_entered",
        mock_exam_id=str(exam.id),
        subjects=len(subject_ids),
        scores_recalculated=rescored,
        entered_by=str(user.id),
    )
    return list_statistics(session, user, exam.id)
