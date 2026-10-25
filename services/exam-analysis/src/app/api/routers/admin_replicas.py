from fastapi import APIRouter

from app.api.dependencies import SessionDep
from app.auth.dependencies import AdminUser
from app.schemas.replicas import BulkUpsertResult, StudentReplicaBulkUpsert
from app.services import replica_service

router = APIRouter(prefix="/admin", tags=["admin"])


@router.put(
    "/student-replicas",
    response_model=BulkUpsertResult,
    summary="Temporary: load students until the StudentAddedToClass consumer exists (week 4)",
)
def upsert_student_replicas(
    request: StudentReplicaBulkUpsert, session: SessionDep, user: AdminUser
) -> BulkUpsertResult:
    return BulkUpsertResult(upserted=replica_service.upsert_from_admin(session, user, request))
