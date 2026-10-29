import uuid

from fastapi import APIRouter

from app.api.dependencies import SessionDep
from app.auth.dependencies import AdminUser
from app.models import WeakTopicSetting
from app.schemas.results import WeakTopicSettingRead, WeakTopicSettingUpdate
from app.services import weak_topic_service

router = APIRouter(prefix="/branches/{branch_id}", tags=["settings"])


@router.get("/weak-topic-setting", response_model=WeakTopicSettingRead)
def get_weak_topic_setting(branch_id: uuid.UUID, session: SessionDep, user: AdminUser) -> WeakTopicSetting:
    return weak_topic_service.get_setting(session, user, branch_id)


@router.put("/weak-topic-setting", response_model=WeakTopicSettingRead)
def put_weak_topic_setting(
    branch_id: uuid.UUID, request: WeakTopicSettingUpdate, session: SessionDep, user: AdminUser
) -> WeakTopicSetting:
    return weak_topic_service.upsert_setting(session, user, branch_id, request)
