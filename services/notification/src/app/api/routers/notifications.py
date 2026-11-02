import uuid
from typing import Annotated

from fastapi import APIRouter, Query, status

from app import inbox
from app.api.dependencies import SessionDep
from app.auth.dependencies import AnyUser
from app.schemas import NotificationView, ReadAllResult, UnreadCount

router = APIRouter(prefix="/notifications", tags=["notifications"])


@router.get("", response_model=list[NotificationView], response_model_by_alias=True)
def list_notifications(
    session: SessionDep,
    user: AnyUser,
    unread_only: Annotated[bool, Query(alias="unreadOnly")] = False,
    limit: Annotated[int, Query(ge=1, le=100)] = 20,
    offset: Annotated[int, Query(ge=0)] = 0,
) -> list[NotificationView]:
    """The caller's in-app notifications, newest first (the bell icon)."""
    rows = inbox.list_notifications(session, user, unread_only=unread_only, limit=limit, offset=offset)
    return [NotificationView.model_validate(row) for row in rows]


@router.get("/unread-count", response_model=UnreadCount, response_model_by_alias=True)
def unread_count(session: SessionDep, user: AnyUser) -> UnreadCount:
    """Polled by the frontend for the number on the bell icon."""
    return UnreadCount(count=inbox.unread_count(session, user))


@router.post("/{notification_id}/read", status_code=status.HTTP_204_NO_CONTENT)
def mark_read(notification_id: uuid.UUID, session: SessionDep, user: AnyUser) -> None:
    inbox.mark_read(session, user, notification_id)


@router.post("/read-all", response_model=ReadAllResult, response_model_by_alias=True)
def mark_all_read(session: SessionDep, user: AnyUser) -> ReadAllResult:
    return ReadAllResult(updated=inbox.mark_all_read(session, user))
