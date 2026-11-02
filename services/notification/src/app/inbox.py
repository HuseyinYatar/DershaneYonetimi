"""In-app notifications of the current user. Every query is scoped to recipient_id = token `sub`."""

import uuid

import structlog
from sqlalchemy import func, select, update
from sqlalchemy.orm import Session

from app.auth.keycloak import CurrentUser
from app.errors import NotFoundError
from app.models import Notification
from app.models.base import utcnow
from app.models.enums import Channel

logger = structlog.get_logger(__name__)


def _own_in_app(user: CurrentUser):
    return (Notification.recipient_id == user.id, Notification.channel == Channel.IN_APP)


def list_notifications(
    session: Session, user: CurrentUser, *, unread_only: bool, limit: int, offset: int
) -> list[Notification]:
    query = select(Notification).where(*_own_in_app(user))
    if unread_only:
        query = query.where(Notification.read_at.is_(None))
    return list(session.scalars(query.order_by(Notification.created_at.desc()).limit(limit).offset(offset)))


def unread_count(session: Session, user: CurrentUser) -> int:
    return session.scalar(select(func.count()).where(*_own_in_app(user), Notification.read_at.is_(None))) or 0


def mark_read(session: Session, user: CurrentUser, notification_id: uuid.UUID) -> None:
    notification = session.get(Notification, notification_id)
    if (
        notification is None
        or notification.recipient_id != user.id
        or notification.channel is not Channel.IN_APP
    ):
        # Someone else's notification answers like a missing one, so ids cannot be probed.
        raise NotFoundError(
            "NOTIFICATION_NOT_FOUND", "Notification not found", notification_id=str(notification_id)
        )
    if notification.read_at is not None:
        logger.debug("notification.already_read", notification_id=str(notification_id))
        return
    notification.read_at = utcnow()
    session.commit()


def mark_all_read(session: Session, user: CurrentUser) -> int:
    result = session.execute(
        update(Notification)
        .where(*_own_in_app(user), Notification.read_at.is_(None))
        .values(read_at=utcnow())
    )
    session.commit()
    return result.rowcount
