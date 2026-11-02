import uuid
from datetime import datetime

from sqlalchemy import String
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, utcnow


class ProcessedEvent(Base):
    """eventIds already handled. SQS delivers at least once; a second delivery is skipped."""

    __tablename__ = "processed_event"

    event_id: Mapped[uuid.UUID] = mapped_column(primary_key=True)
    event_type: Mapped[str] = mapped_column(String(100))
    processed_at: Mapped[datetime] = mapped_column(default=utcnow)
