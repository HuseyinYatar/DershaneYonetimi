import uuid
from decimal import Decimal
from typing import Any

from sqlalchemy import CheckConstraint, ForeignKey, Numeric, String, UniqueConstraint
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.models.base import Base


class ExamType(Base):
    """TYT, AYT, LGS ... The wrong-answer divisor is configuration, never a code constant."""

    __tablename__ = "exam_type"

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    code: Mapped[str] = mapped_column(String(16), unique=True)
    name: Mapped[str] = mapped_column(String(100))
    wrong_divisor: Mapped[Decimal] = mapped_column(Numeric(4, 2))

    __table_args__ = (CheckConstraint("wrong_divisor > 0", name="wrong_divisor_positive"),)


class Subject(Base):
    """A test of an exam type. Standard scores are computed per subject."""

    __tablename__ = "subject"

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    exam_type_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("exam_type.id"), index=True)
    code: Mapped[str] = mapped_column(String(32))
    name: Mapped[str] = mapped_column(String(100))

    __table_args__ = (UniqueConstraint("exam_type_id", "code"),)


class Topic(Base):
    """Curriculum topic. Sub-topics roll up to their parent through parent_topic_id."""

    __tablename__ = "topic"

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    subject_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("subject.id"), index=True)
    name: Mapped[str] = mapped_column(String(200))
    parent_topic_id: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("topic.id"), index=True)

    __table_args__ = (UniqueConstraint("subject_id", "name"),)


class ScoreType(Base):
    """A weighted score of an exam type: TYT, SAY, EA, SOZ, DIL, LGS."""

    __tablename__ = "score_type"

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    exam_type_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("exam_type.id"), index=True)
    code: Mapped[str] = mapped_column(String(16))
    name: Mapped[str] = mapped_column(String(100))
    # Constant added to the weighted standard scores. The final scaling formula is an open
    # decision (see CLAUDE.md); null means no constant is added.
    base_score: Mapped[Decimal | None] = mapped_column(Numeric(7, 3))

    weights: Mapped[list["ScoreTypeWeight"]] = relationship(cascade="all, delete-orphan", lazy="selectin")

    __table_args__ = (UniqueConstraint("exam_type_id", "code"),)


class ScoreTypeWeight(Base):
    __tablename__ = "score_type_weight"

    score_type_id: Mapped[uuid.UUID] = mapped_column( ForeignKey("score_type.id", ondelete="CASCADE"), primary_key=True)
    subject_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("subject.id"), primary_key=True)
    weight: Mapped[Decimal] = mapped_column(Numeric(6, 3))

    __table_args__ = (CheckConstraint("weight > 0", name="weight_positive"),)


class OpticalFormat(Base):
    """Column layout of an optical reader export; validated by OpticalFieldDefinition."""

    __tablename__ = "optical_format"

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    code: Mapped[str] = mapped_column(String(64), unique=True)
    name: Mapped[str] = mapped_column(String(100))
    field_definition: Mapped[dict[str, Any]] = mapped_column(JSONB)
