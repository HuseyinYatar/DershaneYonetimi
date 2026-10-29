import uuid
from datetime import date, datetime

from sqlalchemy import (
    Boolean,
    CheckConstraint,
    Date,
    ForeignKey,
    Index,
    SmallInteger,
    String,
    UniqueConstraint,
)
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, enum_column, utcnow
from app.models.enums import ExamStatus, ScoringSource


class MockExam(Base):
    __tablename__ = "mock_exam"

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    exam_type_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("exam_type.id"), index=True)
    name: Mapped[str] = mapped_column(String(200))
    publisher: Mapped[str | None] = mapped_column(String(200))
    exam_date: Mapped[date] = mapped_column(Date)
    # Branch id owned by the user service; a reference, not a foreign key.
    branch_id: Mapped[uuid.UUID]
    status: Mapped[ExamStatus] = mapped_column(
        enum_column(ExamStatus, "exam_status"), default=ExamStatus.DRAFT
    )
    scoring_source: Mapped[ScoringSource] = mapped_column(enum_column(ScoringSource, "scoring_source"))
    created_by: Mapped[uuid.UUID]
    created_at: Mapped[datetime] = mapped_column(default=utcnow)

    __table_args__ = (Index("ix_mock_exam_branch_date", "branch_id", "exam_date"),)


class AnswerKeyItem(Base):
    """One question of one booklet: question -> subject -> topic -> correct answer."""

    __tablename__ = "answer_key_item"

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    mock_exam_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("mock_exam.id", ondelete="CASCADE"))
    subject_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("subject.id"))
    topic_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("topic.id"))
    booklet: Mapped[str] = mapped_column(String(1))
    question_no: Mapped[int] = mapped_column(SmallInteger)
    correct_answer: Mapped[str] = mapped_column(String(1))
    is_cancelled: Mapped[bool] = mapped_column(Boolean, default=False)

    __table_args__ = (
        UniqueConstraint("mock_exam_id", "booklet", "question_no"),
        CheckConstraint("question_no > 0", name="question_no_positive"),
    )
