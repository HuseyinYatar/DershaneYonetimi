"""E-mail and SMS preferences of the current user. A row in notification_preference = channel turned off."""

from sqlalchemy import delete, select
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.orm import Session

from app.auth.keycloak import CurrentUser
from app.errors import BusinessRuleError
from app.models import NotificationPreference
from app.models.enums import CONFIGURABLE_CHANNELS
from app.schemas import PreferenceItem

# Notification kinds a user can choose channels for. StudentResultAdded follows the
# ExamResultsPublished preference (app.delivery.planner.PREFERENCE_EVENT_TYPES).
CONFIGURABLE_EVENT_TYPES = ("ExamResultsPublished",)


def get_preferences(session: Session, user: CurrentUser) -> list[PreferenceItem]:
    disabled = set(
        session.execute(
            select(NotificationPreference.event_type, NotificationPreference.channel).where(
                NotificationPreference.user_id == user.id
            )
        ).all()
    )
    return [
        PreferenceItem(event_type=event_type, channel=channel, enabled=(event_type, channel) not in disabled)
        for event_type in CONFIGURABLE_EVENT_TYPES
        for channel in CONFIGURABLE_CHANNELS
    ]


def update_preferences(
    session: Session, user: CurrentUser, items: list[PreferenceItem]
) -> list[PreferenceItem]:
    # Validate everything first, so a bad item does not leave half of the list saved.
    for item in items:
        if item.channel not in CONFIGURABLE_CHANNELS:
            raise BusinessRuleError(
                "CHANNEL_NOT_CONFIGURABLE", "Only EMAIL and SMS can be turned off", channel=item.channel.value
            )
        if item.event_type not in CONFIGURABLE_EVENT_TYPES:
            raise BusinessRuleError(
                "EVENT_TYPE_NOT_CONFIGURABLE", "No preference for this event type", event_type=item.event_type
            )

    for item in items:
        if item.enabled:
            session.execute(
                delete(NotificationPreference).where(
                    NotificationPreference.user_id == user.id,
                    NotificationPreference.event_type == item.event_type,
                    NotificationPreference.channel == item.channel,
                )
            )
        else:
            session.execute(
                insert(NotificationPreference)
                .values(user_id=user.id, event_type=item.event_type, channel=item.channel)
                .on_conflict_do_nothing()
            )
    session.commit()
    return get_preferences(session, user)
