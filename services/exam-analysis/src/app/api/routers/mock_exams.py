import uuid
from typing import Annotated

from fastapi import APIRouter, File, Form, Query, UploadFile, status

from app.api.dependencies import CacheDep, SessionDep, SettingsDep, StorageDep
from app.auth.dependencies import AdminUser, StaffUser
from app.models import AnswerKeyItem, ExamSubjectStatistic, ImportJob, MockExam
from app.models.enums import ExamStatus
from app.schemas.exams import AnswerKeyItemRead, AnswerKeyReplace, MockExamCreate, MockExamRead
from app.schemas.imports import ImportJobRead
from app.schemas.results import ExamResultRow, NationalStatisticsUpdate, StatisticRead, TopicAnalysisItem
from app.services import (
    answer_key_service,
    import_job_service,
    mock_exam_service,
    report_service,
    statistics_service,
)

router = APIRouter(prefix="/mock-exams", tags=["mock-exams"])


@router.post("", response_model=MockExamRead, status_code=status.HTTP_201_CREATED)
def create_mock_exam(request: MockExamCreate, session: SessionDep, user: AdminUser) -> MockExam:
    return mock_exam_service.create_mock_exam(session, user, request)


@router.get("", response_model=list[MockExamRead])
def list_mock_exams(
    session: SessionDep,
    user: StaffUser,
    branch_id: Annotated[uuid.UUID, Query(alias="branchId")],
    exam_status: Annotated[ExamStatus | None, Query(alias="status")] = None,
) -> list[MockExam]:
    return mock_exam_service.list_mock_exams(session, user, branch_id, exam_status)


@router.get("/{mock_exam_id}", response_model=MockExamRead)
def get_mock_exam(mock_exam_id: uuid.UUID, session: SessionDep, user: StaffUser) -> MockExam:
    return mock_exam_service.get_for_staff(session, user, mock_exam_id)


@router.put("/{mock_exam_id}/answer-key", response_model=list[AnswerKeyItemRead])
def replace_answer_key(
    mock_exam_id: uuid.UUID, request: AnswerKeyReplace, session: SessionDep, user: AdminUser
) -> list[AnswerKeyItem]:
    return answer_key_service.replace_answer_key(session, user, mock_exam_id, request)


@router.get("/{mock_exam_id}/answer-key", response_model=list[AnswerKeyItemRead])
def get_answer_key(mock_exam_id: uuid.UUID, session: SessionDep, user: StaffUser) -> list[AnswerKeyItem]:
    return answer_key_service.list_answer_key(session, user, mock_exam_id)


@router.post("/{mock_exam_id}/answer-key/{item_id}/cancel", response_model=AnswerKeyItemRead)
def cancel_question(
    mock_exam_id: uuid.UUID, item_id: uuid.UUID, session: SessionDep, user: AdminUser, cache: CacheDep
) -> AnswerKeyItem:
    return answer_key_service.cancel_question(session, user, mock_exam_id, item_id, cache)


@router.post(
    "/{mock_exam_id}/import-jobs", response_model=ImportJobRead, status_code=status.HTTP_202_ACCEPTED
)
def upload_optical_file(
    mock_exam_id: uuid.UUID,
    file: Annotated[UploadFile, File(description="Optical reader export (fixed-width text)")],
    optical_format_id: Annotated[uuid.UUID, Form(alias="opticalFormatId")],
    session: SessionDep,
    storage: StorageDep,
    settings: SettingsDep,
    user: AdminUser,
) -> ImportJob:
    # Read one byte past the limit so oversized files are rejected without loading all of them.
    content = file.file.read(settings.max_upload_bytes + 1)
    return import_job_service.upload_and_create_job(
        session,
        storage,
        user,
        mock_exam_id=mock_exam_id,
        optical_format_id=optical_format_id,
        content=content,
        content_type=file.content_type or "text/plain",
        max_upload_bytes=settings.max_upload_bytes,
    )


@router.post("/{mock_exam_id}/publish", response_model=MockExamRead)
def publish_results(
    mock_exam_id: uuid.UUID, session: SessionDep, user: AdminUser, cache: CacheDep
) -> MockExam:
    return mock_exam_service.publish(session, user, mock_exam_id, cache)


@router.get("/{mock_exam_id}/statistics", response_model=list[StatisticRead])
def list_statistics(
    mock_exam_id: uuid.UUID, session: SessionDep, user: StaffUser
) -> list[ExamSubjectStatistic]:
    return statistics_service.list_statistics(session, user, mock_exam_id)


@router.put("/{mock_exam_id}/statistics/national", response_model=list[StatisticRead])
def set_national_statistics(
    mock_exam_id: uuid.UUID,
    request: NationalStatisticsUpdate,
    session: SessionDep,
    user: AdminUser,
    cache: CacheDep,
) -> list[ExamSubjectStatistic]:
    return statistics_service.set_national_statistics(session, user, mock_exam_id, request, cache)


@router.get("/{mock_exam_id}/results", response_model=list[ExamResultRow])
def exam_results(mock_exam_id: uuid.UUID, session: SessionDep, user: StaffUser) -> list[ExamResultRow]:
    return report_service.exam_results(session, user, mock_exam_id)


@router.get("/{mock_exam_id}/topic-analysis", response_model=list[TopicAnalysisItem])
def topic_analysis(
    mock_exam_id: uuid.UUID, session: SessionDep, user: StaffUser, cache: CacheDep
) -> list[TopicAnalysisItem]:
    return report_service.exam_topic_analysis(session, user, mock_exam_id, cache)
