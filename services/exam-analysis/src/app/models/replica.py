import uuid
from datetime import datetime

from sqlalchemy import Index, String, text
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, utcnow


class StudentReplica(Base):
    """Local copy of students, fed by the user service's events (app.events.handlers).

    Optical rows are matched to active students through (branch_id, student_no). record_version is
    the user's row version in the user service; it stays empty until a UserUpdated was applied.
    """

    __tablename__ = "student_replica"

    student_id: Mapped[uuid.UUID] = mapped_column(primary_key=True)
    student_no: Mapped[str] = mapped_column(String(32))
    branch_id: Mapped[uuid.UUID]
    # Empty while the student is in no classroom.
    class_id: Mapped[uuid.UUID | None]
    full_name: Mapped[str] = mapped_column(String(200))
    is_active: Mapped[bool] = mapped_column(default=True)
    record_version: Mapped[int | None]
    updated_at: Mapped[datetime] = mapped_column(default=utcnow, onupdate=utcnow)

    # Only active students hold a number: a departed student's number can be given to a new one.
    __table_args__ = (
        Index(
            "uq_student_replica_active_student_no",
            "branch_id",
            "student_no",
            unique=True,
            postgresql_where=text("is_active"),
        ),
    )
