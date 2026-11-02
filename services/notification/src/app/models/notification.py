import uuid
from datetime import datetime

from sqlalchemy import ForeignKey, Index, String, Text, UniqueConstraint, text
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, enum_column, utcnow
from app.models.enums import Channel, DeliveryStatus


class ProcessedEvent(Base):
    """eventIds already handled. SQS delivers at least once; a second delivery is skipped."""

    __tablename__ = "processed_event"

    event_id: Mapped[uuid.UUID] = mapped_column(primary_key=True)
    event_type: Mapped[str] = mapped_column(String(100))
    processed_at: Mapped[datetime] = mapped_column(default=utcnow)


class Notification(Base):
    """One message to one recipient on one channel.
    The worker writes the rows while consuming an event (EMAIL/SMS as PENDING, IN_APP as SENT) and
    later delivers the pending ones. The queue message is deleted right after the rows are
    committed, so retries of a failed e-mail are driven by this row, not by SQS.
    """

    __tablename__ = "notification"

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    recipient_id: Mapped[uuid.UUID]
    channel: Mapped[Channel] = mapped_column(enum_column(Channel, "channel"))
    event_type: Mapped[str] = mapped_column(String(100))
    title: Mapped[str] = mapped_column(String(200))
    body: Mapped[str] = mapped_column(Text)
    status: Mapped[DeliveryStatus] = mapped_column(enum_column(DeliveryStatus, "delivery_status"))
    attempt_count: Mapped[int] = mapped_column(default=0)
    next_attempt_at: Mapped[datetime | None]
    # Exception type or SMTP code only; never an address or the text.
    last_error: Mapped[str | None] = mapped_column(String(500))
    sent_at: Mapped[datetime | None]
    # Only IN_APP notifications are read in the app; NULL means unread.
    read_at: Mapped[datetime | None]
    created_at: Mapped[datetime] = mapped_column(default=utcnow)
    source_event_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("processed_event.event_id"))

    __table_args__ = (
        # The same event never notifies the same person twice on the same channel.
        UniqueConstraint("source_event_id", "recipient_id", "channel"),
        Index("ix_notification_pending", "next_attempt_at", postgresql_where=text("status = 'PENDING'")),
        Index(
            "ix_notification_inbox",
            "recipient_id",
            "created_at",
            postgresql_where=text("channel = 'IN_APP'"),
        ),
    )


class NotificationPreference(Base):
    """A channel the user turned off for an event type. No row means the channel is on."""

    __tablename__ = "notification_preference"

    user_id: Mapped[uuid.UUID] = mapped_column(primary_key=True)
    event_type: Mapped[str] = mapped_column(String(100), primary_key=True)
    channel: Mapped[Channel] = mapped_column(enum_column(Channel, "channel"), primary_key=True)
    created_at: Mapped[datetime] = mapped_column(default=utcnow)
