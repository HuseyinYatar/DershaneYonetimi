import uuid

import structlog
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.errors import BusinessRuleError, ConflictError
from app.models import ExamType, OpticalFormat, ScoreType, ScoreTypeWeight, Subject, Topic
from app.repositories import catalog as catalog_repo
from app.schemas.catalog import (
    ExamTypeCreate,
    OpticalFormatCreate,
    ScoreTypeCreate,
    ScoreTypeWeightsUpdate,
    SubjectCreate,
    TopicCreate,
)

logger = structlog.get_logger(__name__)


def create_exam_type(session: Session, request: ExamTypeCreate) -> ExamType:
    """Adds an exam type (e.g. TYT) with its wrong-answer divisor; the code must be unique."""
    if session.scalar(select(ExamType.id).where(ExamType.code == request.code)) is not None:
        raise ConflictError("EXAM_TYPE_CODE_EXISTS", "An exam type with this code exists", code=request.code)
    exam_type = ExamType(code=request.code, name=request.name, wrong_divisor=request.wrong_divisor)
    session.add(exam_type)
    session.commit()
    logger.info("catalog.exam_type_created", exam_type_id=str(exam_type.id), code=exam_type.code)
    return exam_type


def list_exam_types(session: Session) -> list[ExamType]:
    """Returns all exam types ordered by code."""
    return list(session.scalars(select(ExamType).order_by(ExamType.code)))


def create_subject(session: Session, exam_type_id: uuid.UUID, request: SubjectCreate) -> Subject:
    """Adds a subject (test) to an exam type; the code must be unique within the exam type."""
    catalog_repo.get_exam_type(session, exam_type_id)
    duplicate = session.scalar(
        select(Subject.id).where(Subject.exam_type_id == exam_type_id, Subject.code == request.code)
    )
    if duplicate is not None:
        raise ConflictError(
            "SUBJECT_CODE_EXISTS", "The exam type already has this subject code", code=request.code
        )
    subject = Subject(exam_type_id=exam_type_id, code=request.code, name=request.name)
    session.add(subject)
    session.commit()
    logger.info("catalog.subject_created", subject_id=str(subject.id), exam_type_id=str(exam_type_id))
    return subject


def list_subjects(session: Session, exam_type_id: uuid.UUID) -> list[Subject]:
    """Returns the subjects of an exam type ordered by code."""
    catalog_repo.get_exam_type(session, exam_type_id)
    return list(
        session.scalars(select(Subject).where(Subject.exam_type_id == exam_type_id).order_by(Subject.code))
    )


def create_topic(session: Session, subject_id: uuid.UUID, request: TopicCreate) -> Topic:
    """Adds a topic to a subject, optionally under a parent topic of the same subject.

    Topic names are unique within a subject.
    """
    catalog_repo.get_subject(session, subject_id)
    if request.parent_topic_id is not None:
        parent = catalog_repo.get_topic(session, request.parent_topic_id)
        if parent.subject_id != subject_id:
            raise BusinessRuleError(
                "TOPIC_PARENT_IN_OTHER_SUBJECT",
                "A parent topic must belong to the same subject",
                parent_topic_id=str(parent.id),
            )
    duplicate = session.scalar(
        select(Topic.id).where(Topic.subject_id == subject_id, Topic.name == request.name)
    )
    if duplicate is not None:
        raise ConflictError(
            "TOPIC_NAME_EXISTS", "The subject already has a topic with this name", topic_id=str(duplicate)
        )
    topic = Topic(subject_id=subject_id, name=request.name, parent_topic_id=request.parent_topic_id)
    session.add(topic)
    session.commit()
    logger.info("catalog.topic_created", topic_id=str(topic.id), subject_id=str(subject_id))
    return topic


def list_topics(session: Session, subject_id: uuid.UUID) -> list[Topic]:
    """Returns the topics of a subject (parents and children together) ordered by name."""
    catalog_repo.get_subject(session, subject_id)
    return list(session.scalars(select(Topic).where(Topic.subject_id == subject_id).order_by(Topic.name)))


def create_score_type(session: Session, exam_type_id: uuid.UUID, request: ScoreTypeCreate) -> ScoreType:
    """Adds a score type (e.g. SAY, EA) to an exam type; its weights are set with replace_weights."""
    catalog_repo.get_exam_type(session, exam_type_id)
    duplicate = session.scalar(
        select(ScoreType.id).where(ScoreType.exam_type_id == exam_type_id, ScoreType.code == request.code)
    )
    if duplicate is not None:
        raise ConflictError(
            "SCORE_TYPE_CODE_EXISTS", "The exam type already has this score type", code=request.code
        )
    score_type = ScoreType(
        exam_type_id=exam_type_id, code=request.code, name=request.name, base_score=request.base_score
    )
    session.add(score_type)
    session.commit()
    logger.info("catalog.score_type_created", score_type_id=str(score_type.id), code=score_type.code)
    return score_type


def list_score_types(session: Session, exam_type_id: uuid.UUID) -> list[ScoreType]:
    """Returns the score types of an exam type ordered by code."""
    catalog_repo.get_exam_type(session, exam_type_id)
    return list(
        session.scalars(
            select(ScoreType).where(ScoreType.exam_type_id == exam_type_id).order_by(ScoreType.code)
        )
    )


def replace_weights(session: Session, score_type_id: uuid.UUID, request: ScoreTypeWeightsUpdate) -> ScoreType:
    """Replaces all subject weights of a score type.

    Each subject may appear once and must belong to the score type's exam type. Existing exams keep
    their stored scores until they are recalculated (new import, late matching, national statistics
    or a key change).
    """
    score_type = catalog_repo.get_score_type(session, score_type_id)
    requested = [item.subject_id for item in request.weights]
    if len(set(requested)) != len(requested):
        raise BusinessRuleError("DUPLICATE_SUBJECT", "Each subject may appear once in the weights")
    foreign = set(requested) - catalog_repo.subject_ids_of_exam_type(session, score_type.exam_type_id)
    if foreign:
        raise BusinessRuleError(
            "SUBJECT_NOT_IN_EXAM_TYPE",
            "Weights may only use subjects of the score type's exam type",
            subject_ids=sorted(str(subject_id) for subject_id in foreign),
        )
    score_type.weights = [
        ScoreTypeWeight(subject_id=item.subject_id, weight=item.weight) for item in request.weights
    ]
    session.commit()
    logger.info(
        "catalog.score_type_weights_replaced", score_type_id=str(score_type_id), subjects=len(requested)
    )
    return score_type


def create_optical_format(session: Session, request: OpticalFormatCreate) -> OpticalFormat:
    """Registers an optical reader layout: where the student number, booklet and answers sit
    in a line. The code must be unique.
    """
    if session.scalar(select(OpticalFormat.id).where(OpticalFormat.code == request.code)) is not None:
        raise ConflictError(
            "OPTICAL_FORMAT_CODE_EXISTS", "An optical format with this code exists", code=request.code
        )
    optical_format = OpticalFormat(
        code=request.code,
        name=request.name,
        field_definition=request.field_definition.model_dump(by_alias=True),
    )
    session.add(optical_format)
    session.commit()
    logger.info("catalog.optical_format_created", optical_format_id=str(optical_format.id), code=request.code)
    return optical_format


def list_optical_formats(session: Session) -> list[OpticalFormat]:
    """Returns all optical formats ordered by code."""
    return list(session.scalars(select(OpticalFormat).order_by(OpticalFormat.code)))
