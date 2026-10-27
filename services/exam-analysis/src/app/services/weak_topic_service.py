import uuid
from collections.abc import Collection

import structlog
from sqlalchemy import delete, insert, select
from sqlalchemy.orm import Session

from app.auth.access import ensure_branch_staff, ensure_can_view_student
from app.auth.keycloak import CurrentUser
from app.errors import ConflictError, NotFoundError
from app.models import StudentReplica, StudentTopicStatus, Topic, WeakTopicSetting
from app.models.base import utcnow
from app.observability.metrics import WEAK_TOPIC_RECOMPUTE_SKIPPED
from app.repositories import catalog as catalog_repo
from app.repositories import replicas as replicas_repo
from app.repositories import results as results_repo
from app.repositories.frames import to_records
from app.schemas.results import WeakTopicRead, WeakTopicReport, WeakTopicSettingUpdate
from app.scoring.weak_topics import build_topic_ancestry, compute_topic_statuses

logger = structlog.get_logger(__name__)


def recompute_for_students( session: Session, branch_id: uuid.UUID, student_ids: Collection[uuid.UUID]) -> None:
    """Rebuilds the weak topic summary (student_topic_status) of the given students.

    Uses the branch's window and threshold over published exams only; the summary is rebuilt, never
    patched. Skipped with a warning and a metric when the branch has no setting. Does not commit.
    """
    setting = session.get(WeakTopicSetting, branch_id)
    if setting is None:
        WEAK_TOPIC_RECOMPUTE_SKIPPED.labels(reason="setting_missing").inc()
        logger.warning(
            "weak_topic.recompute_skipped",
            reason="setting_missing",
            branch_id=str(branch_id),
            students=len(student_ids),
        )
        return
    if not student_ids:
        logger.info("weak_topic.recompute_skipped", reason="no_students", branch_id=str(branch_id))
        return

    session.flush()
    # acıklanmış tum deneme sonuclarını döndürüyor
    topic_results = results_repo.published_topic_results(session, student_ids)
    statuses = compute_topic_statuses(
        topic_results,
        build_topic_ancestry(catalog_repo.topic_parents(session)),
        window_size=setting.window_size,
        threshold=float(setting.success_threshold),
    )
    session.execute(delete(StudentTopicStatus).where(StudentTopicStatus.student_id.in_(list(student_ids))))
    if not statuses.empty:
        rows = statuses.assign(success_rate=statuses["success_rate"].round(4), updated_at=utcnow())
        session.execute(insert(StudentTopicStatus), to_records(rows))
    logger.info(
        "weak_topic.recomputed",
        branch_id=str(branch_id),
        students=len(student_ids),
        topic_statuses=len(statuses),
        weak_topics=int(statuses["is_weak"].sum()) if not statuses.empty else 0,
        window_size=setting.window_size,
    )


def get_setting(session: Session, user: CurrentUser, branch_id: uuid.UUID) -> WeakTopicSetting:
    """Returns the branch's weak topic setting (window size, success threshold)."""
    ensure_branch_staff(user, branch_id)
    setting = session.get(WeakTopicSetting, branch_id)
    if setting is None:
        raise NotFoundError(
            "WEAK_TOPIC_SETTING_MISSING", "No weak topic setting for this branch", branch_id=str(branch_id)
        )
    return setting


def upsert_setting(
    session: Session, user: CurrentUser, branch_id: uuid.UUID, request: WeakTopicSettingUpdate
) -> WeakTopicSetting:
    """Creates or updates the branch's weak topic setting and recomputes its students' weak topics."""
    ensure_branch_staff(user, branch_id)
    setting = session.get(WeakTopicSetting, branch_id)
    if setting is None:
        setting = WeakTopicSetting(branch_id=branch_id)
        session.add(setting)
    setting.window_size = request.window_size
    setting.success_threshold = request.success_threshold
    setting.updated_by = user.id
    setting.updated_at = utcnow()
    session.flush()
    recompute_for_students(session, branch_id, replicas_repo.branch_student_ids(session, branch_id))
    session.commit()
    logger.info(
        "weak_topic.setting_updated",
        branch_id=str(branch_id),
        window_size=request.window_size,
        success_threshold=str(request.success_threshold),
        updated_by=str(user.id),
    )
    return setting


def weak_topic_report(session: Session, user: CurrentUser, student_id: uuid.UUID) -> WeakTopicReport:
    """Returns the student's weak topics, weakest first (the "study these topics" list)."""
    ensure_can_view_student(session, user, student_id)
    replica = session.get(StudentReplica, student_id)
    if replica is None:
        raise NotFoundError(
            "STUDENT_NOT_FOUND", "Student is unknown to this service", student_id=str(student_id)
        )
    setting = session.get(WeakTopicSetting, replica.branch_id)
    if setting is None:
        raise ConflictError(
            "WEAK_TOPIC_SETTING_MISSING",
            "An admin has to configure the weak topic window for this branch",
            branch_id=str(replica.branch_id),
        )
    rows = session.execute(
        select(StudentTopicStatus, Topic)
        .join(Topic, Topic.id == StudentTopicStatus.topic_id)
        .where(StudentTopicStatus.student_id == student_id, StudentTopicStatus.is_weak.is_(True))
        .order_by(StudentTopicStatus.success_rate, Topic.name)
    ).all()
    weak_topics = [
        WeakTopicRead(
            topic_id=topic.id,
            topic_name=topic.name,
            subject_id=topic.subject_id,
            parent_topic_id=topic.parent_topic_id,
            total_questions=status.total_questions,
            total_correct=status.total_correct,
            measured_exam_count=status.measured_exam_count,
            success_rate=status.success_rate,
        )
        for status, topic in rows
    ]
    #WeakTopicRead ve WeakTopicReport shema kısmında tasarlandı
    return WeakTopicReport(
        student_id=student_id,
        window_size=setting.window_size,
        success_threshold=setting.success_threshold,
        weak_topics=weak_topics,
    )
