"""Local copies of user service data. Only consumed events write these tables (never the API)."""

import uuid
from datetime import datetime

from sqlalchemy import String
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, enum_column, utcnow
from app.models.enums import Relationship, UserRole


class ContactReplica(Base):
    """Name and contact details of every user, fed by UserUpdated (full current state, upsert).

    record_version is the user's row version in the user service; SNS does not keep order, so an
    event older than the stored version is ignored.
    """

    __tablename__ = "contact_replica"

    user_id: Mapped[uuid.UUID] = mapped_column(primary_key=True)
    role: Mapped[UserRole] = mapped_column(enum_column(UserRole, "user_role"))
    branch_id: Mapped[uuid.UUID]
    first_name: Mapped[str] = mapped_column(String(100))
    last_name: Mapped[str] = mapped_column(String(100))
    email: Mapped[str | None] = mapped_column(String(254))
    phone: Mapped[str | None] = mapped_column(String(32))
    active: Mapped[bool]
    record_version: Mapped[int]
    updated_at: Mapped[datetime] = mapped_column(default=utcnow, onupdate=utcnow)


class GuardianLinkReplica(Base):
    """Guardian of each student, fed by GuardianLinked / GuardianUnlinked.

    The key is student_id alone: a student has at most one guardian (CLAUDE.md, user service rules).
    """

    __tablename__ = "guardian_link_replica"

    student_id: Mapped[uuid.UUID] = mapped_column(primary_key=True)
    guardian_id: Mapped[uuid.UUID] = mapped_column(index=True)
    relationship: Mapped[Relationship] = mapped_column(enum_column(Relationship, "relationship"))
    updated_at: Mapped[datetime] = mapped_column(default=utcnow, onupdate=utcnow)
