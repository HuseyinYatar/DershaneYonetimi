from fastapi import APIRouter

from app import preferences
from app.api.dependencies import SessionDep
from app.auth.dependencies import AnyUser
from app.schemas import PreferenceItem, PreferencesUpdate

router = APIRouter(prefix="/notification-preferences", tags=["preferences"])


@router.get("", response_model=list[PreferenceItem], response_model_by_alias=True)
def get_preferences(session: SessionDep, user: AnyUser) -> list[PreferenceItem]:
    """E-mail and SMS on/off per notification kind. In-app notifications are always on."""
    return preferences.get_preferences(session, user)


@router.put("", response_model=list[PreferenceItem], response_model_by_alias=True)
def update_preferences(body: PreferencesUpdate, session: SessionDep, user: AnyUser) -> list[PreferenceItem]:
    return preferences.update_preferences(session, user, body.preferences)
