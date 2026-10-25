import uuid
from datetime import datetime
from decimal import Decimal

from sqlalchemy import Boolean, CheckConstraint, ForeignKey, Numeric, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, enum_column, utcnow
from app.models.enums import ScoringSource


class ExamSubjectStatistic(Base):
    """Mean and standard deviation of one subject's nets, used for standard scores.

    INSTITUTIONAL rows are computed from the branch's students; NATIONAL rows are entered by an
    admin once the publisher announces them.
    """

    __tablename__ = "exam_subject_statistic"

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    mock_exam_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("mock_exam.id", ondelete="CASCADE"))
    subject_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("subject.id"))
    source: Mapped[ScoringSource] = mapped_column(enum_column(ScoringSource, "scoring_source"))
    mean: Mapped[Decimal] = mapped_column(Numeric(7, 3))
    std_dev: Mapped[Decimal] = mapped_column(Numeric(7, 3))
    participant_count: Mapped[int]
    # Admin who entered NATIONAL statistics; null for computed rows.
    updated_by: Mapped[uuid.UUID | None]
    updated_at: Mapped[datetime] = mapped_column(default=utcnow, onupdate=utcnow)

    __table_args__ = (UniqueConstraint("mock_exam_id", "subject_id", "source"),)


class StudentTopicStatus(Base):
    """Summary of a student's last N published exams for one topic (recomputed, never patched)."""

    __tablename__ = "student_topic_status"

    student_id: Mapped[uuid.UUID] = mapped_column(primary_key=True)
    topic_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("topic.id"), primary_key=True)
    total_questions: Mapped[int]
    total_correct: Mapped[int]
    measured_exam_count: Mapped[int]
    success_rate: Mapped[Decimal] = mapped_column(Numeric(5, 4))
    is_weak: Mapped[bool] = mapped_column(Boolean)
    updated_at: Mapped[datetime] = mapped_column(default=utcnow)


class WeakTopicSetting(Base):
    """Per-branch weak topic window and threshold. Only admins change it."""

    __tablename__ = "weak_topic_setting"

    branch_id: Mapped[uuid.UUID] = mapped_column(primary_key=True)
    window_size: Mapped[int]
    success_threshold: Mapped[Decimal] = mapped_column(Numeric(5, 4))
    updated_by: Mapped[uuid.UUID]
    updated_at: Mapped[datetime] = mapped_column(default=utcnow, onupdate=utcnow)

    __table_args__ = (
        CheckConstraint("window_size > 0", name="window_size_positive"),
        CheckConstraint("success_threshold > 0 AND success_threshold <= 1", name="threshold_range"),
    )
