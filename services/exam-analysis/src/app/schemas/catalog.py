import uuid
from decimal import Decimal
from typing import Any

from pydantic import Field

from app.schemas.common import ApiModel
from app.schemas.optical import OpticalFieldDefinition


class ExamTypeCreate(ApiModel):
    code: str = Field(min_length=1, max_length=16, pattern=r"^[A-Z0-9_]+$")
    name: str = Field(min_length=1, max_length=100)
    wrong_divisor: Decimal = Field(gt=0, max_digits=4, decimal_places=2)


class ExamTypeRead(ApiModel):
    id: uuid.UUID
    code: str
    name: str
    wrong_divisor: Decimal


class SubjectCreate(ApiModel):
    code: str = Field(min_length=1, max_length=32, pattern=r"^[A-Z0-9_]+$")
    name: str = Field(min_length=1, max_length=100)


class SubjectRead(ApiModel):
    id: uuid.UUID
    exam_type_id: uuid.UUID
    code: str
    name: str


class TopicCreate(ApiModel):
    name: str = Field(min_length=1, max_length=200)
    parent_topic_id: uuid.UUID | None = None


class TopicRead(ApiModel):
    id: uuid.UUID
    subject_id: uuid.UUID
    name: str
    parent_topic_id: uuid.UUID | None


class ScoreTypeCreate(ApiModel):
    code: str = Field(min_length=1, max_length=16, pattern=r"^[A-Z0-9_]+$")
    name: str = Field(min_length=1, max_length=100)
    base_score: Decimal | None = Field(default=None, max_digits=7, decimal_places=3)


class ScoreTypeWeightItem(ApiModel):
    subject_id: uuid.UUID
    weight: Decimal = Field(gt=0, max_digits=6, decimal_places=3)


class ScoreTypeWeightsUpdate(ApiModel):
    weights: list[ScoreTypeWeightItem] = Field(min_length=1)


class ScoreTypeRead(ApiModel):
    id: uuid.UUID
    exam_type_id: uuid.UUID
    code: str
    name: str
    base_score: Decimal | None
    weights: list[ScoreTypeWeightItem]


class OpticalFormatCreate(ApiModel):
    code: str = Field(min_length=1, max_length=64, pattern=r"^[a-z0-9-]+$")
    name: str = Field(min_length=1, max_length=100)
    field_definition: OpticalFieldDefinition


class OpticalFormatRead(ApiModel):
    id: uuid.UUID
    code: str
    name: str
    field_definition: dict[str, Any]
