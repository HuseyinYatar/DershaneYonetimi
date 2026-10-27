import uuid
from datetime import datetime
from typing import Any

from sqlalchemy import Index, String, Text, text
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, utcnow


class OutboxEvent(Base):
    """Events written in the same transaction as the change that caused them.

    The worker's relay publishes unpublished rows to SNS, so a committed change never loses its
    event and an event is never sent for a rolled-back change.
    """

    __tablename__ = "outbox_event"

    # Equals the envelope's eventId; consumers deduplicate on it.
    id: Mapped[uuid.UUID] = mapped_column(primary_key=True)
    event_type: Mapped[str] = mapped_column(String(100))
    envelope: Mapped[dict[str, Any]] = mapped_column(JSONB)
    # W3C trace context of the request that caused the event; sent as an SNS message attribute
    # because the envelope schema allows no extra fields.
    traceparent: Mapped[str | None] = mapped_column(String(64))
    created_at: Mapped[datetime] = mapped_column(default=utcnow)
    published_at: Mapped[datetime | None]
    attempts: Mapped[int] = mapped_column(default=0)
    last_error: Mapped[str | None] = mapped_column(Text)

    __table_args__ = (
        Index("ix_outbox_event_unpublished", "created_at", postgresql_where=text("published_at IS NULL")),
    )
