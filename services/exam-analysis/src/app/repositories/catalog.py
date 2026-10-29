import uuid

import pandas as pd
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.errors import NotFoundError
from app.models import ExamType, OpticalFormat, ScoreType, ScoreTypeWeight, Subject, Topic
from app.repositories.frames import read_frame


def get_exam_type(session: Session, exam_type_id: uuid.UUID) -> ExamType:
    exam_type = session.get(ExamType, exam_type_id)
    if exam_type is None:
        raise NotFoundError("EXAM_TYPE_NOT_FOUND", "Exam type not found", exam_type_id=str(exam_type_id))
    return exam_type


def get_subject(session: Session, subject_id: uuid.UUID) -> Subject:
    subject = session.get(Subject, subject_id)
    if subject is None:
        raise NotFoundError("SUBJECT_NOT_FOUND", "Subject not found", subject_id=str(subject_id))
    return subject


def get_topic(session: Session, topic_id: uuid.UUID) -> Topic:
    topic = session.get(Topic, topic_id)
    if topic is None:
        raise NotFoundError("TOPIC_NOT_FOUND", "Topic not found", topic_id=str(topic_id))
    return topic


def get_score_type(session: Session, score_type_id: uuid.UUID) -> ScoreType:
    score_type = session.get(ScoreType, score_type_id)
    if score_type is None:
        raise NotFoundError("SCORE_TYPE_NOT_FOUND", "Score type not found", score_type_id=str(score_type_id))
    return score_type


def get_optical_format(session: Session, optical_format_id: uuid.UUID) -> OpticalFormat:
    optical_format = session.get(OpticalFormat, optical_format_id)
    if optical_format is None:
        raise NotFoundError(
            "OPTICAL_FORMAT_NOT_FOUND", "Optical format not found", optical_format_id=str(optical_format_id)
        )
    return optical_format


def subject_ids_of_exam_type(session: Session, exam_type_id: uuid.UUID) -> set[uuid.UUID]:
    return set(session.scalars(select(Subject.id).where(Subject.exam_type_id == exam_type_id)))


def topics_by_id(session: Session, topic_ids: set[uuid.UUID]) -> dict[uuid.UUID, Topic]:
    return {topic.id: topic for topic in session.scalars(select(Topic).where(Topic.id.in_(topic_ids)))}


def topic_parents(session: Session) -> dict[uuid.UUID, uuid.UUID | None]:
    return dict(session.execute(select(Topic.id, Topic.parent_topic_id)).all())


def score_type_weights(
    session: Session, exam_type_id: uuid.UUID
) -> tuple[pd.DataFrame, dict[uuid.UUID, float | None], dict[uuid.UUID, str]]:
    """Returns (weights frame: score_type_id, subject_id, weight), base scores and codes by id."""
    score_types = session.scalars(select(ScoreType).where(ScoreType.exam_type_id == exam_type_id)).all()
    weights = read_frame(
        session,
        select(ScoreTypeWeight.score_type_id, ScoreTypeWeight.subject_id, ScoreTypeWeight.weight)
        .join(ScoreType, ScoreType.id == ScoreTypeWeight.score_type_id)
        .where(ScoreType.exam_type_id == exam_type_id),
        ["score_type_id", "subject_id", "weight"],
    )
    weights["weight"] = weights["weight"].astype(float)
    base_scores = {st.id: (None if st.base_score is None else float(st.base_score)) for st in score_types}
    codes = {st.id: st.code for st in score_types}
    return weights, base_scores, codes
