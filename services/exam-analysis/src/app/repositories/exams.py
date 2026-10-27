import uuid

import pandas as pd
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.errors import NotFoundError
from app.models import AnswerKeyItem, MockExam
from app.repositories.frames import read_frame
from app.scoring.grader import ANSWER_KEY_COLUMNS


def get_mock_exam(session: Session, mock_exam_id: uuid.UUID, *, for_update: bool = False) -> MockExam:
    """for_update locks the exam row: every operation that rewrites results holds this lock, so
    statistics and ranks of one exam are never recomputed concurrently."""
    statement = select(MockExam).where(MockExam.id == mock_exam_id)
    if for_update:
        statement = statement.with_for_update()
    exam = session.scalars(statement).one_or_none()
    if exam is None:
        raise NotFoundError("MOCK_EXAM_NOT_FOUND", "Mock exam not found", mock_exam_id=str(mock_exam_id))
    return exam


def get_answer_key_item(session: Session, mock_exam_id: uuid.UUID, item_id: uuid.UUID) -> AnswerKeyItem:
    item = session.get(AnswerKeyItem, item_id)
    if item is None or item.mock_exam_id != mock_exam_id:
        raise NotFoundError(
            "ANSWER_KEY_ITEM_NOT_FOUND",
            "Answer key item not found in this exam",
            mock_exam_id=str(mock_exam_id),
            answer_key_item_id=str(item_id),
        )
    return item


def answer_key_items(session: Session, mock_exam_id: uuid.UUID) -> list[AnswerKeyItem]:
    return list(
        session.scalars(
            select(AnswerKeyItem)
            .where(AnswerKeyItem.mock_exam_id == mock_exam_id)
            .order_by(AnswerKeyItem.booklet, AnswerKeyItem.question_no)
        )
    )


def answer_key_frame(session: Session, mock_exam_id: uuid.UUID) -> pd.DataFrame:
    return read_frame(
        session,
        select(
            AnswerKeyItem.id,
            AnswerKeyItem.booklet,
            AnswerKeyItem.question_no,
            AnswerKeyItem.subject_id,
            AnswerKeyItem.topic_id,
            AnswerKeyItem.correct_answer,
            AnswerKeyItem.is_cancelled,
        ).where(AnswerKeyItem.mock_exam_id == mock_exam_id),
        ANSWER_KEY_COLUMNS,
    )
