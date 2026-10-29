import uuid
from datetime import datetime

from sqlalchemy import String, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, utcnow


class StudentReplica(Base):
    """Local copy of students, fed by StudentAddedToClass (until then by an admin endpoint).

    Optical rows are matched to students through (branch_id, student_no).
    """

    __tablename__ = "student_replica"

    student_id: Mapped[uuid.UUID] = mapped_column(primary_key=True)
    student_no: Mapped[str] = mapped_column(String(32))
    branch_id: Mapped[uuid.UUID]
    class_id: Mapped[uuid.UUID]
    full_name: Mapped[str] = mapped_column(String(200))
    updated_at: Mapped[datetime] = mapped_column(default=utcnow, onupdate=utcnow)

    __table_args__ = (UniqueConstraint("branch_id", "student_no"),)
