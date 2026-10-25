"""Event contracts. JSON Schemas of the same events live in shared/events/ and a contract test
keeps them in sync with these models."""

import uuid
from datetime import datetime
from typing import Any

from pydantic import Field

from app.schemas.common import ApiModel

PRODUCER = "exam-analysis"

class EventEnvelope(ApiModel):
    event_id: uuid.UUID
    event_type: str
    version: int = 1
    occurred_at: datetime
    producer: str = PRODUCER
    traceparent: str | None = None
    payload: dict[str, Any]


class ExamResultsPublished(ApiModel):
    """Published once per exam when an admin publishes the results."""
    mock_exam_id: uuid.UUID
    branch_id: uuid.UUID
    exam_type_code: str
    student_ids: list[uuid.UUID] = Field(min_length=1)


class StudentResultAdded(ApiModel):
    """A result added to an already published exam (late matching of an unmatched row)."""

    mock_exam_id: uuid.UUID
    branch_id: uuid.UUID
    student_id: uuid.UUID


class StudentAddedToClass(ApiModel):
    """Produced by the user service; consumed here to maintain student_replica (week 4)."""

    student_id: uuid.UUID
    student_no: str
    branch_id: uuid.UUID
    class_id: uuid.UUID
    full_name: str


PUBLISHED_EVENTS: dict[str, type[ApiModel]] = {
    "ExamResultsPublished": ExamResultsPublished,
    "StudentResultAdded": StudentResultAdded,
}
CONSUMED_EVENTS: dict[str, type[ApiModel]] = {"StudentAddedToClass": StudentAddedToClass}
