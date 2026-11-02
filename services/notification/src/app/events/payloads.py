"""Consumed event contracts. The JSON Schemas of the same events live in shared/events/ and a contract
test keeps these models in sync with them.

Consumers must ignore unknown fields (shared/events/README.md), so the models allow extra keys:
a producer adding an optional field does not break this service.
"""

import uuid
from datetime import date, datetime
from typing import Any

from pydantic import ConfigDict, Field

from app.models.enums import Relationship, UserRole
from app.schemas import ApiModel


class EventModel(ApiModel):
    model_config = ConfigDict(extra="ignore")


class EventEnvelope(EventModel):
    """Wrapper of every event (shared/events/envelope.schema.json)."""

    event_id: uuid.UUID
    event_type: str
    schema_version: int
    occurred_at: datetime
    source: str
    data: dict[str, Any]


class UserUpdated(EventModel):
    """Full current state of a user (user service). Feeds contact_replica."""

    user_id: uuid.UUID
    role: UserRole
    branch_id: uuid.UUID
    first_name: str = Field(min_length=1)
    last_name: str = Field(min_length=1)
    email: str | None
    phone: str | None
    active: bool
    record_version: int = Field(ge=0)


class GuardianLinked(EventModel):
    guardian_id: uuid.UUID
    student_id: uuid.UUID
    relationship: Relationship


class GuardianUnlinked(EventModel):
    guardian_id: uuid.UUID
    student_id: uuid.UUID


class ExamResultsPublished(EventModel):
    """Results of a mock exam were published (exam-analysis). student_ids: students with a result."""

    mock_exam_id: uuid.UUID
    branch_id: uuid.UUID
    exam_type_code: str
    student_ids: list[uuid.UUID] = Field(min_length=1)
    # Optional (added later); the texts fall back to the exam type when they are missing.
    mock_exam_name: str | None = None
    exam_date: date | None = None


class StudentResultAdded(EventModel):
    """A late-matched student got a result in an already published exam (exam-analysis)."""

    mock_exam_id: uuid.UUID
    branch_id: uuid.UUID
    student_id: uuid.UUID
    mock_exam_name: str | None = None
    exam_date: date | None = None
