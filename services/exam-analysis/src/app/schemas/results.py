import uuid
from datetime import date, datetime
from decimal import Decimal
from typing import Literal

from pydantic import Field

from app.models.enums import ScoringSource
from app.schemas.common import ApiModel


class StatisticRead(ApiModel):
    subject_id: uuid.UUID
    source: ScoringSource
    mean: Decimal
    std_dev: Decimal
    participant_count: int
    updated_at: datetime


class NationalStatisticItem(ApiModel):
    subject_id: uuid.UUID
    mean: Decimal = Field(max_digits=7, decimal_places=3)
    std_dev: Decimal = Field(gt=0, max_digits=7, decimal_places=3)
    participant_count: int = Field(gt=0)


class NationalStatisticsUpdate(ApiModel):
    items: list[NationalStatisticItem] = Field(min_length=1)


class ScoreRead(ApiModel):
    """Always an estimate; the UI labels it "tahmini (kurum içi)" or "tahmini (Türkiye geneli)"."""

    score_type_id: uuid.UUID
    score_type_code: str
    estimated_score: Decimal | None
    is_estimated: Literal[True] = True
    source: ScoringSource | None
    branch_rank: int | None


class ExamResultRow(ApiModel):
    student_id: uuid.UUID
    full_name: str | None
    booklet: str
    correct: int
    wrong: int
    blank: int
    net: Decimal
    branch_rank: int | None
    scores: list[ScoreRead]


class StudentExamSummary(ApiModel):
    mock_exam_id: uuid.UUID
    exam_name: str
    exam_type_id: uuid.UUID
    exam_date: date
    net: Decimal
    branch_rank: int | None
    scores: list[ScoreRead]


class SubjectResultRead(ApiModel):
    subject_id: uuid.UUID
    subject_name: str
    correct: int
    wrong: int
    blank: int
    net: Decimal


class TopicResultRead(ApiModel):
    topic_id: uuid.UUID
    topic_name: str
    question_count: int
    correct: int
    success_rate: Decimal


class StudentResultDetail(StudentExamSummary):
    booklet: str
    correct: int
    wrong: int
    blank: int
    subjects: list[SubjectResultRead]
    topics: list[TopicResultRead]


class ProgressSubjectNet(ApiModel):
    subject_id: uuid.UUID
    net: Decimal


class ProgressPoint(ApiModel):
    mock_exam_id: uuid.UUID
    exam_name: str
    exam_date: date
    net: Decimal
    subjects: list[ProgressSubjectNet]


class StudentProgress(ApiModel):
    student_id: uuid.UUID
    exam_type_id: uuid.UUID
    points: list[ProgressPoint]


class TopicAnalysisItem(ApiModel):
    topic_id: uuid.UUID
    topic_name: str
    subject_id: uuid.UUID
    student_count: int
    question_count: int
    correct: int
    success_rate: float


class WeakTopicRead(ApiModel):
    topic_id: uuid.UUID
    topic_name: str
    subject_id: uuid.UUID
    parent_topic_id: uuid.UUID | None
    total_questions: int
    total_correct: int
    measured_exam_count: int
    success_rate: Decimal


class WeakTopicReport(ApiModel):
    """Weak topics, weakest first: the "study these topics" recommendation for students and teachers."""

    student_id: uuid.UUID
    window_size: int
    success_threshold: Decimal
    weak_topics: list[WeakTopicRead]


class WeakTopicSettingUpdate(ApiModel):
    window_size: int = Field(gt=0, le=50)
    success_threshold: Decimal = Field(gt=0, le=1, max_digits=5, decimal_places=4)


class WeakTopicSettingRead(ApiModel):
    branch_id: uuid.UUID
    window_size: int
    success_threshold: Decimal
    updated_by: uuid.UUID
    updated_at: datetime
