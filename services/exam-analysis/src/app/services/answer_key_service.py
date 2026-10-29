import uuid

import pandas as pd
import structlog
from sqlalchemy import delete, insert
from sqlalchemy.orm import Session

from app.auth.keycloak import CurrentUser
from app.cache import ResultCache
from app.errors import BusinessRuleError, ConflictError
from app.models import AnswerKeyItem, MockExam
from app.models.enums import ExamStatus
from app.repositories import catalog as catalog_repo
from app.repositories import exams as exams_repo
from app.repositories import results as results_repo
from app.repositories.frames import to_records
from app.schemas.exams import AnswerKeyReplace
from app.scoring.grader import regrade
from app.services import mock_exam_service, result_writer, scoring_service, weak_topic_service

logger = structlog.get_logger(__name__)

EDITABLE_STATUSES = {ExamStatus.DRAFT, ExamStatus.KEY_READY}
SCORED_STATUSES = {ExamStatus.PROCESSED, ExamStatus.PUBLISHED}


def replace_answer_key(
    session: Session, user: CurrentUser, mock_exam_id: uuid.UUID, request: AnswerKeyReplace
) -> list[AnswerKeyItem]:
    """Replaces the whole answer key of an exam and marks the exam KEY_READY.

    Allowed only before an optical file is processed (DRAFT or KEY_READY); after that, single
    questions are cancelled instead. The key is checked by _validate_key before anything is written.
    """
    exam = mock_exam_service.get_for_staff(session, user, mock_exam_id, for_update=True)
    if exam.status not in EDITABLE_STATUSES:
        raise ConflictError(
            "ANSWER_KEY_LOCKED",
            "The key cannot be replaced once results exist; cancel single questions instead",
            mock_exam_id=str(exam.id),
            status=exam.status.value,
        )
    items = pd.DataFrame([item.model_dump() for item in request.items])
    _validate_key(session, exam, items)

    session.execute(delete(AnswerKeyItem).where(AnswerKeyItem.mock_exam_id == exam.id))
    rows = items.assign(
        id=[uuid.uuid4() for _ in range(len(items))], mock_exam_id=exam.id, is_cancelled=False
    )
    session.execute(insert(AnswerKeyItem), to_records(rows))
    exam.status = ExamStatus.KEY_READY
    session.commit()
    logger.info( "answer_key.replaced", mock_exam_id=str(exam.id),
        items=len(items),
        booklets=sorted(items["booklet"].unique().tolist()),
    )
    return exams_repo.answer_key_items(session, exam.id)


def _validate_key(session: Session, exam: MockExam, items: pd.DataFrame) -> None:
    """Rejects a key that cannot be graded.

    Checks: no question number twice in a booklet, every subject belongs to the exam type, every
    topic exists and belongs to its question's subject, every booklet is numbered 1..N with the same N.
    """
    duplicates = items.duplicated(subset=["booklet", "question_no"])
    if duplicates.any():
        raise BusinessRuleError(
            "DUPLICATE_QUESTION",
            "A question number appears twice in a booklet",
            duplicates=int(duplicates.sum()),
        )

    foreign_subjects = set(items["subject_id"]) - catalog_repo.subject_ids_of_exam_type(
        session, exam.exam_type_id
    )
    if foreign_subjects:
        raise BusinessRuleError(
            "SUBJECT_NOT_IN_EXAM_TYPE",
            "Every question must belong to a subject of the exam's type",
            subject_ids=sorted(str(subject_id) for subject_id in foreign_subjects),
        )

    topics = catalog_repo.topics_by_id(session, set(items["topic_id"]))
    unknown_topics = set(items["topic_id"]) - topics.keys()
    if unknown_topics:
        raise BusinessRuleError(
            "TOPIC_NOT_FOUND", "Unknown topics in the key", topic_ids=sorted(str(t) for t in unknown_topics)
        )
    topic_subject = items["topic_id"].map({topic_id: topic.subject_id for topic_id, topic in topics.items()})
    mismatched = items[topic_subject != items["subject_id"]]
    if not mismatched.empty:
        raise BusinessRuleError(
            "TOPIC_NOT_IN_SUBJECT",
            "A question's topic must belong to the question's subject",
            question_nos=sorted(mismatched["question_no"].astype(int).tolist()),
        )

    per_booklet = items.groupby("booklet")["question_no"].agg(["count", "max"])
    gaps = per_booklet[per_booklet["count"] != per_booklet["max"]]
    if not gaps.empty or per_booklet["count"].nunique() != 1:
        raise BusinessRuleError(
            "ANSWER_KEY_INCOMPLETE",
            "Every booklet must number its questions 1..N with the same N",
            question_counts={booklet: int(count) for booklet, count in per_booklet["count"].items()},
        )


def list_answer_key(session: Session, user: CurrentUser, mock_exam_id: uuid.UUID) -> list[AnswerKeyItem]:
    """Returns the exam's answer key ordered by booklet and question number (branch staff only)."""
    exam = mock_exam_service.get_for_staff(session, user, mock_exam_id)
    return exams_repo.answer_key_items(session, exam.id)


def cancel_question(
    session: Session, user: CurrentUser, mock_exam_id: uuid.UUID, item_id: uuid.UUID, cache: ResultCache
) -> AnswerKeyItem:
    """Cancels one question of the key and, if results exist, re-grades them.

    Cancelled questions count as neither correct, wrong nor blank (ÖSYM practice). A question appears
    once per booklet with different numbers, so each booklet's item is cancelled separately.
    """
    exam = mock_exam_service.get_for_staff(session, user, mock_exam_id, for_update=True)
    item = exams_repo.get_answer_key_item(session, exam.id, item_id)
    if item.is_cancelled:
        raise ConflictError(
            "ANSWER_KEY_ITEM_ALREADY_CANCELLED",
            "The question is already cancelled",
            answer_key_item_id=str(item.id),
        )
    item.is_cancelled = True
    session.flush()

    changed_answers = rescore_exam(session, exam) if exam.status in SCORED_STATUSES else 0
    student_ids = sorted(results_repo.existing_student_ids(session, exam.id))
    session.commit()
    cache.invalidate_exam(exam.id, exam.exam_type_id, student_ids)
    logger.info(
        "answer_key.question_cancelled",
        mock_exam_id=str(exam.id),
        answer_key_item_id=str(item.id),
        rescored=exam.status in SCORED_STATUSES,
        changed_answers=changed_answers,
        cancelled_by=str(user.id),
    )
    return item


def rescore_exam(session: Session, exam: MockExam) -> int:
    """Re-grades the stored answers against the current key; no optical file is needed.

    Rebuilds subject and topic results, statistics, scores and ranks, and for a published exam the
    students' weak topics. Returns the number of answers whose status changed.
    """
    regraded = regrade(
        results_repo.stored_answers_frame(session, exam.id), exams_repo.answer_key_frame(session, exam.id)
    )
    if regraded.empty:
        logger.info("answer_key.rescore_skipped", reason="no_results", mock_exam_id=str(exam.id))
        return 0
    exam_type = catalog_repo.get_exam_type(session, exam.exam_type_id)
    changed = result_writer.rewrite_after_regrade(session, exam.id, regraded, float(exam_type.wrong_divisor))
    scoring_service.recalculate_exam(session, exam, refresh_institutional=True)
    if exam.status == ExamStatus.PUBLISHED:
        weak_topic_service.recompute_for_students(
            session, exam.branch_id, sorted(results_repo.existing_student_ids(session, exam.id))
        )
    return changed
