import uuid
from datetime import datetime

from sqlalchemy import BigInteger, ForeignKey, String, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, enum_column, utcnow
from app.models.enums import FileKind, FilePurpose


class StoredFile(Base):
    """Metadata of one uploaded file; the content is in S3 under files/{id}."""

    __tablename__ = "file"
    # A repeated upload with the same Idempotency-Key finds the first one through this constraint.
    __table_args__ = (UniqueConstraint("owner_id", "idempotency_key"),)

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True)
    purpose: Mapped[FilePurpose] = mapped_column(enum_column(FilePurpose, "file_purpose"))
    # Keycloak `sub` of the uploader (a teacher, a student or a service account).
    owner_id: Mapped[uuid.UUID]
    kind: Mapped[FileKind] = mapped_column(enum_column(FileKind, "file_kind"))
    size_bytes: Mapped[int] = mapped_column(BigInteger)
    # As sent by the browser; may contain a person's name, so it is never logged.
    file_name: Mapped[str] = mapped_column(String(255))
    # SHA-256 of the bytes as uploaded (before photos are re-encoded): tells a repeated request
    # with the same Idempotency-Key apart from a different file sent under that key.
    source_sha256: Mapped[str] = mapped_column(String(64))
    idempotency_key: Mapped[str | None] = mapped_column(String(255))
    created_at: Mapped[datetime] = mapped_column(default=utcnow)

    def __repr__(self) -> str:
        return f"StoredFile(id={self.id})"


class FileAccess(Base):
    """Who besides the owner may read a file. Rows are only ever added."""

    __tablename__ = "file_access"

    file_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("file.id"), primary_key=True)
    user_id: Mapped[uuid.UUID] = mapped_column(primary_key=True)

    def __repr__(self) -> str:
        return f"FileAccess(file_id={self.file_id})"
