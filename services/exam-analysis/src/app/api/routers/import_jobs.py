import uuid
from typing import Annotated

from fastapi import APIRouter, Query

from app.api.dependencies import CacheDep, SessionDep
from app.auth.dependencies import AdminUser, StaffUser
from app.models import ImportJob, UnmatchedRow
from app.models.enums import MatchStatus
from app.schemas.imports import ImportJobRead, MatchRowRequest, UnmatchedRowRead
from app.services import import_job_service, matching_service

router = APIRouter(tags=["imports"])


@router.get("/import-jobs/{job_id}", response_model=ImportJobRead)
def get_import_job(job_id: uuid.UUID, session: SessionDep, user: StaffUser) -> ImportJob:
    return import_job_service.get_job(session, user, job_id)


@router.get("/import-jobs/{job_id}/unmatched-rows", response_model=list[UnmatchedRowRead])
def list_unmatched_rows(
    job_id: uuid.UUID,
    session: SessionDep,
    user: AdminUser,
    row_status: Annotated[MatchStatus | None, Query(alias="status")] = None,
) -> list[UnmatchedRow]:
    return import_job_service.list_unmatched_rows(session, user, job_id, row_status)


@router.post("/unmatched-rows/{row_id}/match", response_model=UnmatchedRowRead)
def match_unmatched_row(
    row_id: uuid.UUID, request: MatchRowRequest, session: SessionDep, user: AdminUser, cache: CacheDep
) -> UnmatchedRow:
    return matching_service.match_row(session, user, row_id, request.student_id, cache)


@router.post("/unmatched-rows/{row_id}/ignore", response_model=UnmatchedRowRead)
def ignore_unmatched_row(row_id: uuid.UUID, session: SessionDep, user: AdminUser) -> UnmatchedRow:
    return matching_service.ignore_row(session, user, row_id)
