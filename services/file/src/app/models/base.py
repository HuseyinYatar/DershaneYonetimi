from datetime import UTC, datetime
from enum import StrEnum

from sqlalchemy import DateTime, Enum, MetaData
from sqlalchemy.orm import DeclarativeBase

NAMING_CONVENTION = {
    "ix": "ix_%(column_0_label)s",
    "uq": "uq_%(table_name)s_%(column_0_N_name)s",
    "ck": "ck_%(table_name)s_%(constraint_name)s",
    "fk": "fk_%(table_name)s_%(column_0_name)s_%(referred_table_name)s",
    "pk": "pk_%(table_name)s",
}


class Base(DeclarativeBase):
    metadata = MetaData(naming_convention=NAMING_CONVENTION)
    type_annotation_map = {datetime: DateTime(timezone=True)}


def utcnow() -> datetime:
    return datetime.now(UTC)


def enum_column(enum_class: type[StrEnum], name: str) -> Enum:
    """VARCHAR + CHECK instead of a native PostgreSQL enum, so new values need no ALTER TYPE."""
    return Enum(enum_class, name=name, native_enum=False, create_constraint=True, length=32)
