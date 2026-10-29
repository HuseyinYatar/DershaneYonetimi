import uuid
from collections.abc import Collection

import pandas as pd
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models import MockExam, StudentAnswer, StudentResult, SubjectResult, TopicResult
from app.models.enums import ExamStatus
from app.repositories.frames import read_frame


def existing_student_ids(session: Session, mock_exam_id: uuid.UUID) -> set[uuid.UUID]:
    return set(
        session.scalars(select(StudentResult.student_id).where(StudentResult.mock_exam_id == mock_exam_id))
    )


def result_ids_statement(mock_exam_id: uuid.UUID):
    return select(StudentResult.id).where(StudentResult.mock_exam_id == mock_exam_id)


def subject_nets_frame(session: Session, mock_exam_id: uuid.UUID) -> pd.DataFrame:
    frame = read_frame(
        session,
        select(SubjectResult.student_result_id, SubjectResult.subject_id, SubjectResult.net)
        .join(StudentResult, StudentResult.id == SubjectResult.student_result_id)
        .where(StudentResult.mock_exam_id == mock_exam_id),
        ["result_key", "subject_id", "net"],
    )
    frame["net"] = frame["net"].astype(float)
    return frame


def totals_frame(session: Session, mock_exam_id: uuid.UUID) -> pd.DataFrame:
    frame = read_frame(
        session,
        select(StudentResult.id, StudentResult.net).where(StudentResult.mock_exam_id == mock_exam_id),
        ["id", "net"],
    )
    frame["net"] = frame["net"].astype(float)
    return frame


def stored_answers_frame(session: Session, mock_exam_id: uuid.UUID) -> pd.DataFrame:
    return read_frame(
        session,
        select(
            StudentAnswer.id,
            StudentAnswer.student_result_id,
            StudentAnswer.answer_key_item_id,
            StudentAnswer.given_answer,
            StudentAnswer.status,
        )
        .join(StudentResult, StudentResult.id == StudentAnswer.student_result_id)
        .where(StudentResult.mock_exam_id == mock_exam_id),
        ["answer_id", "sheet_key", "answer_key_item_id", "given_answer", "previous_status"],
    )


def published_topic_results(session: Session, student_ids: Collection[uuid.UUID]) -> pd.DataFrame:
    """Topic results of the students over published exams only (the weak topic window)."""
    frame = read_frame(
        session,
        select(
            StudentResult.student_id,
            MockExam.exam_type_id,
            MockExam.id,
            MockExam.exam_date,
            TopicResult.topic_id,
            TopicResult.question_count,
            TopicResult.correct,
        )
        .join(StudentResult, StudentResult.id == TopicResult.student_result_id)
        .join(MockExam, MockExam.id == StudentResult.mock_exam_id)
        .where(MockExam.status == ExamStatus.PUBLISHED, StudentResult.student_id.in_(list(student_ids))),
        ["student_id", "exam_type_id", "mock_exam_id", "exam_date", "topic_id", "question_count", "correct"],
    )
    return frame
