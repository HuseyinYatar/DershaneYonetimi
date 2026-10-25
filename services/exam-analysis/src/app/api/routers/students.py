import uuid
from typing import Annotated

from fastapi import APIRouter, Query

from app.api.dependencies import CacheDep, SessionDep
from app.auth.dependencies import AnyUser
from app.schemas.results import StudentExamSummary, StudentProgress, StudentResultDetail, WeakTopicReport
from app.services import report_service, weak_topic_service

router = APIRouter(prefix="/students/{student_id}", tags=["students"])


@router.get("/results", response_model=list[StudentExamSummary])
def student_results(student_id: uuid.UUID, session: SessionDep, user: AnyUser) -> list[StudentExamSummary]:
    return report_service.student_results(session, user, student_id)


@router.get("/results/{mock_exam_id}", response_model=StudentResultDetail)
def student_result_detail(
    student_id: uuid.UUID, mock_exam_id: uuid.UUID, session: SessionDep, user: AnyUser
) -> StudentResultDetail:
    return report_service.student_result_detail(session, user, student_id, mock_exam_id)


@router.get("/progress", response_model=StudentProgress)
def student_progress(
    student_id: uuid.UUID,
    exam_type_id: Annotated[uuid.UUID, Query(alias="examTypeId")],
    session: SessionDep,
    user: AnyUser,
    cache: CacheDep,
) -> StudentProgress:
    return report_service.student_progress(session, user, student_id, exam_type_id, cache)


@router.get("/weak-topics", response_model=WeakTopicReport)
def weak_topics(student_id: uuid.UUID, session: SessionDep, user: AnyUser) -> WeakTopicReport:
    return weak_topic_service.weak_topic_report(session, user, student_id)
