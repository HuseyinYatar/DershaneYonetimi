import uuid

from pydantic import Field

from app.schemas.common import ApiModel


class StudentReplicaIn(ApiModel):
    student_id: uuid.UUID
    student_no: str = Field(min_length=1, max_length=32)
    branch_id: uuid.UUID
    class_id: uuid.UUID
    full_name: str = Field(min_length=1, max_length=200)


class StudentReplicaBulkUpsert(ApiModel):
    students: list[StudentReplicaIn] = Field(min_length=1, max_length=5000)


class BulkUpsertResult(ApiModel):
    upserted: int
