import uuid

from fastapi import APIRouter, status

from app.api.dependencies import SessionDep
from app.auth.dependencies import AdminUser, AnyUser
from app.models import ExamType, OpticalFormat, ScoreType, Subject, Topic
from app.schemas.catalog import (
    ExamTypeCreate,
    ExamTypeRead,
    OpticalFormatCreate,
    OpticalFormatRead,
    ScoreTypeCreate,
    ScoreTypeRead,
    ScoreTypeWeightsUpdate,
    SubjectCreate,
    SubjectRead,
    TopicCreate,
    TopicRead,
)
from app.services import catalog_service

router = APIRouter(tags=["catalog"])


@router.get("/exam-types", response_model=list[ExamTypeRead])
def list_exam_types(session: SessionDep, _: AnyUser) -> list[ExamType]:
    return catalog_service.list_exam_types(session)


@router.post("/exam-types", response_model=ExamTypeRead, status_code=status.HTTP_201_CREATED)
def create_exam_type(request: ExamTypeCreate, session: SessionDep, _: AdminUser) -> ExamType:
    return catalog_service.create_exam_type(session, request)


@router.get("/exam-types/{exam_type_id}/subjects", response_model=list[SubjectRead])
def list_subjects(exam_type_id: uuid.UUID, session: SessionDep, _: AnyUser) -> list[Subject]:
    return catalog_service.list_subjects(session, exam_type_id)


@router.post(
    "/exam-types/{exam_type_id}/subjects", response_model=SubjectRead, status_code=status.HTTP_201_CREATED
)
def create_subject(
    exam_type_id: uuid.UUID, request: SubjectCreate, session: SessionDep, _: AdminUser
) -> Subject:
    return catalog_service.create_subject(session, exam_type_id, request)


@router.get("/subjects/{subject_id}/topics", response_model=list[TopicRead])
def list_topics(subject_id: uuid.UUID, session: SessionDep, _: AnyUser) -> list[Topic]:
    return catalog_service.list_topics(session, subject_id)


@router.post("/subjects/{subject_id}/topics", response_model=TopicRead, status_code=status.HTTP_201_CREATED)
def create_topic(subject_id: uuid.UUID, request: TopicCreate, session: SessionDep, _: AdminUser) -> Topic:
    return catalog_service.create_topic(session, subject_id, request)


@router.get("/exam-types/{exam_type_id}/score-types", response_model=list[ScoreTypeRead])
def list_score_types(exam_type_id: uuid.UUID, session: SessionDep, _: AnyUser) -> list[ScoreType]:
    return catalog_service.list_score_types(session, exam_type_id)


@router.post(
    "/exam-types/{exam_type_id}/score-types",
    response_model=ScoreTypeRead,
    status_code=status.HTTP_201_CREATED,
)
def create_score_type(
    exam_type_id: uuid.UUID, request: ScoreTypeCreate, session: SessionDep, _: AdminUser
) -> ScoreType:
    return catalog_service.create_score_type(session, exam_type_id, request)


@router.put("/score-types/{score_type_id}/weights", response_model=ScoreTypeRead)
def replace_score_type_weights(
    score_type_id: uuid.UUID, request: ScoreTypeWeightsUpdate, session: SessionDep, _: AdminUser
) -> ScoreType:
    return catalog_service.replace_weights(session, score_type_id, request)


@router.get("/optical-formats", response_model=list[OpticalFormatRead])
def list_optical_formats(session: SessionDep, _: AdminUser) -> list[OpticalFormat]:
    return catalog_service.list_optical_formats(session)


@router.post("/optical-formats", response_model=OpticalFormatRead, status_code=status.HTTP_201_CREATED)
def create_optical_format(request: OpticalFormatCreate, session: SessionDep, _: AdminUser) -> OpticalFormat:
    return catalog_service.create_optical_format(session, request)
