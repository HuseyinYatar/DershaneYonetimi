import uuid
from decimal import Decimal
from typing import Any

from sqlalchemy import CheckConstraint, ForeignKey, Numeric, SmallInteger, String, UniqueConstraint
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
    # Coefficient model (app.scoring.coefficient_score) when coefficient_year is set: base_score is the
    # score at zero net, weights are points per net. Otherwise (LGS until its coefficients are known)
    # the standard-score model: base_score is added to the weighted standard scores.
    base_score: Mapped[Decimal | None] = mapped_column(Numeric(7, 3))
    # Year the coefficients estimate; shown as "tahmini (2026 katsayılarıyla)".
    coefficient_year: Mapped[int | None] = mapped_column(SmallInteger)
    # Net one minimum-net group must reach (ÖSYM: 0.5), and the cap of the score (500).
    minimum_net: Mapped[Decimal | None] = mapped_column(Numeric(4, 2))
    max_score: Mapped[Decimal | None] = mapped_column(Numeric(7, 3))

    weights: Mapped[list[ScoreTypeWeight]] = relationship(cascade="all, delete-orphan", lazy="selectin")

    __table_args__ = (UniqueConstraint("exam_type_id", "code"),)


class ScoreTypeWeight(Base):
    __tablename__ = "score_type_weight"

    score_type_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("score_type.id", ondelete="CASCADE"), primary_key=True
    )
    subject_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("subject.id"), primary_key=True)
    # Points per net (coefficient model) or the weight of the standard score (standard-score model).
    # The subject may belong to another exam type: SAY/EA/SÖZ/DİL also count the TYT tests.
    weight: Mapped[Decimal] = mapped_column(Numeric(6, 3))
    # Subjects with the same group number form one ÖSYM test for the minimum-net rule (e.g. Fizik,
    # Kimya and Biyoloji are the Fen Bilimleri test); the score needs one group at minimum_net.
    minimum_net_group: Mapped[int | None] = mapped_column(SmallInteger)

    __table_args__ = (CheckConstraint("weight > 0", name="weight_positive"),)


class OpticalFormat(Base):
    """Column layout of an optical reader export; validated by OpticalFieldDefinition."""

    __tablename__ = "optical_format"

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    code: Mapped[str] = mapped_column(String(64), unique=True)
    name: Mapped[str] = mapped_column(String(100))
    field_definition: Mapped[dict[str, Any]] = mapped_column(JSONB)
