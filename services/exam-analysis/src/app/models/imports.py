import uuid
from datetime import datetime

from sqlalchemy import ForeignKey, Index, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, enum_column, utcnow
from app.models.enums import ImportSource, JobStatus, MatchStatus, UnmatchedReason

# txt dosyası yüklendi anında okunamadı bu bir job bu jobu tutmak için
class ImportJob(Base):
    """An optical file import. The table doubles as the worker queue (FOR UPDATE SKIP LOCKED)."""

    __tablename__ = "import_job"

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    mock_exam_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("mock_exam.id", ondelete="CASCADE"), index=True
    )
    optical_format_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("optical_format.id"))
    # File id in object storage (owned by the file service once it exists).
    file_id: Mapped[uuid.UUID]
    source: Mapped[ImportSource] = mapped_column(enum_column(ImportSource, "import_source"))
    # Upstream job id for OMR imports.
    source_ref: Mapped[str | None] = mapped_column(String(64))
    status: Mapped[JobStatus] = mapped_column(enum_column(JobStatus, "job_status"), default=JobStatus.QUEUED)
    total_rows: Mapped[int] = mapped_column(default=0)
    succeeded_rows: Mapped[int] = mapped_column(default=0)
    failed_rows: Mapped[int] = mapped_column(default=0)
    # Why the whole file failed. Row-level problems go to unmatched_row instead.
    error_message: Mapped[str | None] = mapped_column(Text)
    requested_by: Mapped[uuid.UUID]
    created_at: Mapped[datetime] = mapped_column(default=utcnow)
    started_at: Mapped[datetime | None]
    finished_at: Mapped[datetime | None]

    __table_args__ = (Index("ix_import_job_queue", "status", "created_at"),)


# bir öğrenciye bağlanmayan cevapları tutmak için
class UnmatchedRow(Base):
    """An optical row that produced no result. Never deleted; an admin matches or ignores it."""

    __tablename__ = "unmatched_row"

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    import_job_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("import_job.id", ondelete="CASCADE"))
    line_no: Mapped[int]
    raw_student_no: Mapped[str] = mapped_column(String(64))
    raw_line: Mapped[str] = mapped_column(Text)
    reason: Mapped[UnmatchedReason] = mapped_column(enum_column(UnmatchedReason, "unmatched_reason"))
    status: Mapped[MatchStatus] = mapped_column(
        enum_column(MatchStatus, "match_status"), default=MatchStatus.PENDING
    )
    # File id of the photographed answer sheet (OMR imports) so the admin can look at it.
    sheet_image_file_id: Mapped[uuid.UUID | None]
    matched_student_id: Mapped[uuid.UUID | None]
    resolved_by: Mapped[uuid.UUID | None]
    resolved_at: Mapped[datetime | None]

    __table_args__ = (Index("ix_unmatched_row_job_status", "import_job_id", "status"),)
