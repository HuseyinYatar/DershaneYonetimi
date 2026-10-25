import uuid
from datetime import datetime

from app.models.enums import ImportSource, JobStatus, MatchStatus, UnmatchedReason
from app.schemas.common import ApiModel


class ImportJobRead(ApiModel):
    id: uuid.UUID
    mock_exam_id: uuid.UUID
    optical_format_id: uuid.UUID
    file_id: uuid.UUID
    source: ImportSource
    source_ref: str | None
    status: JobStatus
    total_rows: int
    succeeded_rows: int
    failed_rows: int
    error_message: str | None
    requested_by: uuid.UUID
    created_at: datetime
    started_at: datetime | None
    finished_at: datetime | None


class UnmatchedRowRead(ApiModel):
    id: uuid.UUID
    import_job_id: uuid.UUID
    line_no: int
    raw_student_no: str
    raw_line: str
    reason: UnmatchedReason
    status: MatchStatus
    sheet_image_file_id: uuid.UUID | None
    matched_student_id: uuid.UUID | None
    resolved_by: uuid.UUID | None
    resolved_at: datetime | None


class MatchRowRequest(ApiModel):
    student_id: uuid.UUID
