import uuid
from datetime import date, datetime

from pydantic import Field

from app.models.enums import ExamStatus, ScoringSource
from app.schemas.common import ApiModel


class MockExamCreate(ApiModel):
    exam_type_id: uuid.UUID
    name: str = Field(min_length=1, max_length=200)
    publisher: str | None = Field(default=None, max_length=200)
    exam_date: date
    branch_id: uuid.UUID
    scoring_source: ScoringSource


class MockExamRead(ApiModel):
    id: uuid.UUID
    exam_type_id: uuid.UUID
    name: str
    publisher: str | None
    exam_date: date
    branch_id: uuid.UUID
    status: ExamStatus
    scoring_source: ScoringSource
    created_at: datetime


class AnswerKeyItemIn(ApiModel):
    booklet: str = Field(min_length=1, max_length=1, pattern=r"^[A-Z]$")
    question_no: int = Field(gt=0, le=500)
    subject_id: uuid.UUID
    topic_id: uuid.UUID
    correct_answer: str = Field(min_length=1, max_length=1, pattern=r"^[A-E]$")


class AnswerKeyReplace(ApiModel):
    items: list[AnswerKeyItemIn] = Field(min_length=1)


class AnswerKeyItemRead(ApiModel):
    id: uuid.UUID
    booklet: str
    question_no: int
    subject_id: uuid.UUID
    topic_id: uuid.UUID
    correct_answer: str
    is_cancelled: bool
