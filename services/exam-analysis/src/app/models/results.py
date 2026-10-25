import uuid
from datetime import datetime
from decimal import Decimal

from sqlalchemy import ForeignKey, Index, Numeric, SmallInteger, String, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, enum_column, utcnow
from app.models.enums import AnswerStatus, ScoringSource


class StudentResult(Base):
    __tablename__ = "student_result"

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    mock_exam_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("mock_exam.id", ondelete="CASCADE"))
    # Keycloak sub of the student (user service).
    student_id: Mapped[uuid.UUID] = mapped_column(index=True)
    booklet: Mapped[str] = mapped_column(String(1))
    correct: Mapped[int]
    wrong: Mapped[int]
    blank: Mapped[int]
    net: Mapped[Decimal] = mapped_column(Numeric(6, 2))
    # Rank by total net within the exam (an exam belongs to a single branch).
    branch_rank: Mapped[int | None]
    created_at: Mapped[datetime] = mapped_column(default=utcnow)

    # One result per student per exam: a second optical row can never be matched to the same student.
    __table_args__ = (UniqueConstraint("mock_exam_id", "student_id"),)


class StudentScore(Base):
    """Estimated score of one result for one score type (TYT has one, AYT has SAY/EA/SOZ)."""

    __tablename__ = "student_score"

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    student_result_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("student_result.id", ondelete="CASCADE"))
    score_type_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("score_type.id"))
    # Null until the statistics it depends on exist (e.g. national statistics not entered yet).
    estimated_score: Mapped[Decimal | None] = mapped_column(Numeric(7, 3))
    source: Mapped[ScoringSource | None] = mapped_column(enum_column(ScoringSource, "scoring_source"))
    branch_rank: Mapped[int | None]

    __table_args__ = (UniqueConstraint("student_result_id", "score_type_id"),)


class StudentAnswer(Base):
    __tablename__ = "student_answer"

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    student_result_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("student_result.id", ondelete="CASCADE"))
    answer_key_item_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("answer_key_item.id"))
    question_no: Mapped[int] = mapped_column(SmallInteger)
    # Null for a blank answer; '*' marks multiple marks on an OMR sheet.
    given_answer: Mapped[str | None] = mapped_column(String(1))
    status: Mapped[AnswerStatus] = mapped_column(enum_column(AnswerStatus, "answer_status"))

    __table_args__ = (
        UniqueConstraint("student_result_id", "answer_key_item_id"),
        Index("ix_student_answer_key_item", "answer_key_item_id"),
    )


class SubjectResult(Base):
    __tablename__ = "subject_result"

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    student_result_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("student_result.id", ondelete="CASCADE"))
    subject_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("subject.id"))
    correct: Mapped[int]
    wrong: Mapped[int]
    blank: Mapped[int]
    net: Mapped[Decimal] = mapped_column(Numeric(6, 2))

    __table_args__ = (UniqueConstraint("student_result_id", "subject_id"),)


class TopicResult(Base):
    __tablename__ = "topic_result"

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    student_result_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("student_result.id", ondelete="CASCADE"))
    topic_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("topic.id"))
    # Cancelled questions are excluded.
    question_count: Mapped[int]
    correct: Mapped[int]
    success_rate: Mapped[Decimal] = mapped_column(Numeric(5, 4))

    __table_args__ = (UniqueConstraint("student_result_id", "topic_id"),)
