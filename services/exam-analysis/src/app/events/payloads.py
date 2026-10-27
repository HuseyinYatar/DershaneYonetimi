"""Event contracts. JSON Schemas of the same events live in shared/events/ (envelope.schema.json and
<source>/<EventType>.schema.json) and a contract test keeps them in sync with these models."""

import uuid
from datetime import date, datetime
from typing import Any

from pydantic import Field

from app.schemas.common import ApiModel

SOURCE = "exam-analysis"

class EventEnvelope(ApiModel):
    """Wrapper of every event (shared/events/envelope.schema.json). No other fields are allowed."""
    event_id: uuid.UUID
    event_type: str
    schema_version: int = 1
    occurred_at: datetime
    source: str = SOURCE
    data: dict[str, Any]


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


class StudentAddedToClassroom(ApiModel):
    """Produced by the user service; consumed here to maintain student_replica (week 4)."""
    membership_id: uuid.UUID
    student_id: uuid.UUID
    classroom_id: uuid.UUID
    branch_id: uuid.UUID
    student_number: str
    full_name: str
    start_date: date


PUBLISHED_EVENTS: dict[str, type[ApiModel]] = {
    "ExamResultsPublished": ExamResultsPublished,
    "StudentResultAdded": StudentResultAdded,
}
CONSUMED_EVENTS: dict[str, type[ApiModel]] = {"StudentAddedToClassroom": StudentAddedToClassroom}
