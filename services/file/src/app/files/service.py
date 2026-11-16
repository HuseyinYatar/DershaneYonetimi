"""Use cases: upload a file, and hand a file to someone it is shared with."""

import hashlib
import unicodedata
import uuid
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from urllib.parse import quote

import structlog
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.auth.keycloak import CurrentUser
from app.config import Settings
from app.errors import BusinessRuleError, ForbiddenError, NotFoundError, PayloadTooLargeError
from app.files.inspection import ImageLimits, detect_kind, sanitize_image
from app.files.rules import RULES, PurposeRule, Recipients
from app.files.storage import FileStorage
from app.models import FileAccess, StoredFile
from app.models.enums import FileKind, FilePurpose
from app.observability.metrics import APPLICATION, FILE_READS, FILE_UPLOAD_BYTES, FILE_UPLOADS

logger = structlog.get_logger(__name__)

MIB = 1024 * 1024


@dataclass(frozen=True)
class UploadCommand:
    purpose: FilePurpose
    data: bytes
    file_name: str | None
    student_ids: list[uuid.UUID]
    teacher_id: uuid.UUID | None
    idempotency_key: str | None


@dataclass(frozen=True)
class DownloadUrl:
    url: str
    expires_at: datetime


def upload_file(
    session: Session, storage: FileStorage, settings: Settings, user: CurrentUser, command: UploadCommand
) -> StoredFile:
    rule = RULES[command.purpose]
    if not user.has_any_role(*rule.uploader_roles):
        raise ForbiddenError(
            "PURPOSE_NOT_ALLOWED",
            "The caller's roles may not upload files of this purpose",
            purpose=command.purpose.value,
            log_context={"actor_id": str(user.id)},
        )
    recipients = _recipients(rule, command, settings.max_recipients, owner_id=user.id)
    if not command.data:
        raise BusinessRuleError("FILE_EMPTY", "The uploaded file is empty")
    source_sha256 = hashlib.sha256(command.data).hexdigest()

    if command.idempotency_key is not None:
        existing = _find_by_key(session, user.id, command.idempotency_key)
        if existing is not None:
            return _replay(session, existing, command.purpose, source_sha256, recipients)

    kind = detect_kind(command.data, rule.kinds)
    limit = settings.max_image_bytes if kind.is_image else settings.max_document_bytes
    if len(command.data) > limit:
        raise PayloadTooLargeError(
            "FILE_TOO_LARGE", "The file exceeds the size limit", maxMegabytes=limit // MIB
        )
    content = command.data
    if kind.is_image:
        content = sanitize_image(
            command.data, kind, ImageLimits(settings.image_max_pixels, settings.image_max_edge_px)
        )

    stored = StoredFile(
        id=uuid.uuid4(),
        purpose=command.purpose,
        owner_id=user.id,
        kind=kind,
        size_bytes=len(content),
        file_name=_clean_file_name(command.file_name, kind),
        source_sha256=source_sha256,
        idempotency_key=command.idempotency_key,
    )
    # Content first: a row is only written for a file S3 has. If the commit fails afterwards the
    # object stays unreferenced (bilinen sınır: like files never attached to a task).
    storage.put(stored.id, content, kind.content_type)
    try:
        session.add(stored)
        # The models have no relationship(), so the file row is flushed first for the foreign key.
        session.flush()
        session.add_all(FileAccess(file_id=stored.id, user_id=user_id) for user_id in recipients)
        session.commit()
    except IntegrityError:
        # Two requests with the same Idempotency-Key at the same time: the other one won.
        session.rollback()
        existing = (
            _find_by_key(session, user.id, command.idempotency_key) if command.idempotency_key else None
        )
        if existing is None:
            raise
        return _replay(session, existing, command.purpose, source_sha256, recipients)

    FILE_UPLOADS.labels(application=APPLICATION, purpose=stored.purpose.value, outcome="stored").inc()
    FILE_UPLOAD_BYTES.labels(application=APPLICATION, purpose=stored.purpose.value).observe(stored.size_bytes)
    logger.info(
        "file.uploaded",
        file_id=str(stored.id),
        owner_id=str(user.id),
        purpose=stored.purpose.value,
        content_type=kind.value,
        size_bytes=stored.size_bytes,
        recipient_count=len(recipients),
    )
    return stored


def _recipients(
    rule: PurposeRule, command: UploadCommand, max_recipients: int, *, owner_id: uuid.UUID
) -> set[uuid.UUID]:
    given = set(command.student_ids)
    if command.teacher_id is not None:
        given.add(command.teacher_id)
    expected_field = {Recipients.STUDENTS: "studentIds", Recipients.TEACHER: "teacherId"}.get(rule.recipients)
    wrong_field = (rule.recipients is not Recipients.STUDENTS and command.student_ids) or (
        rule.recipients is not Recipients.TEACHER and command.teacher_id is not None
    )
    if wrong_field:
        raise BusinessRuleError(
            "RECIPIENTS_NOT_ALLOWED",
            "This purpose takes no such recipient field",
            purpose=command.purpose.value,
            expectedField=expected_field,
        )
    if expected_field is not None and not given:
        raise BusinessRuleError(
            "RECIPIENTS_REQUIRED", "The file must be shared with someone", expectedField=expected_field
        )
    if len(given) > max_recipients:
        raise BusinessRuleError("TOO_MANY_RECIPIENTS", "Too many recipients", maxRecipients=max_recipients)
    # The owner reads the file as its owner; a row for them would change nothing.
    given.discard(owner_id)
    return given


def _find_by_key(session: Session, owner_id: uuid.UUID, key: str) -> StoredFile | None:
    return session.scalars(
        select(StoredFile).where(StoredFile.owner_id == owner_id, StoredFile.idempotency_key == key)
    ).one_or_none()


def _replay(
    session: Session,
    existing: StoredFile,
    purpose: FilePurpose,
    source_sha256: str,
    recipients: set[uuid.UUID],
) -> StoredFile:
    """Same Idempotency-Key: the first upload is answered again if the request is the same one."""
    same_request = (
        existing.purpose is purpose
        and existing.source_sha256 == source_sha256
        and _shared_with(session, existing.id) == recipients
    )
    if not same_request:
        raise BusinessRuleError(
            "IDEMPOTENCY_KEY_REUSED",
            "The Idempotency-Key was used for a different upload",
            log_context={"file_id": str(existing.id)},
        )
    FILE_UPLOADS.labels(application=APPLICATION, purpose=purpose.value, outcome="replayed").inc()
    logger.info("file.upload_replayed", file_id=str(existing.id), owner_id=str(existing.owner_id))
    return existing


def _shared_with(session: Session, file_id: uuid.UUID) -> set[uuid.UUID]:
    return set(session.scalars(select(FileAccess.user_id).where(FileAccess.file_id == file_id)))


def _clean_file_name(raw: str | None, kind: FileKind) -> str:
    # Browsers send only the base name, other clients may send a path; control characters would
    # break the Content-Disposition header of the download.
    name = (raw or "").replace("\\", "/").rsplit("/", 1)[-1]
    name = "".join(ch for ch in name if unicodedata.category(ch)[0] != "C").strip()
    return (name or f"dosya{kind.extension}")[:255]


def get_readable_file(session: Session, user: CurrentUser, file_id: uuid.UUID) -> StoredFile:
    """The file, if the caller may read it. Missing and not shared look the same from outside."""
    stored = session.get(StoredFile, file_id)
    if stored is None:
        raise NotFoundError(
            "FILE_NOT_FOUND", "File not found", file_id=str(file_id), log_context={"reason": "missing"}
        )
    if not _can_read(session, user, stored):
        raise NotFoundError(
            "FILE_NOT_FOUND",
            "File not found",
            file_id=str(file_id),
            log_context={"reason": "not_shared", "actor_id": str(user.id)},
        )
    return stored


def _can_read(session: Session, user: CurrentUser, stored: StoredFile) -> bool:
    if stored.owner_id == user.id:
        return True
    if user.has_any_role(*RULES[stored.purpose].reader_roles):
        return True
    return session.get(FileAccess, {"file_id": stored.id, "user_id": user.id}) is not None


def issue_download_url(
    storage: FileStorage, settings: Settings, user: CurrentUser, stored: StoredFile
) -> DownloadUrl:
    url = storage.download_url(
        stored.id,
        content_type=stored.kind.content_type,
        content_disposition=content_disposition(stored),
        expires_in=settings.download_url_ttl_seconds,
    )
    FILE_READS.labels(application=APPLICATION, purpose=stored.purpose.value, access="download_url").inc()
    # Who received which file: the audit trail of the files minors share.
    logger.info("file.download_url_issued", file_id=str(stored.id), actor_id=str(user.id))
    return DownloadUrl(
        url=url, expires_at=datetime.now(UTC) + timedelta(seconds=settings.download_url_ttl_seconds)
    )


def read_content(storage: FileStorage, user: CurrentUser, stored: StoredFile) -> bytes:
    content = storage.get(stored.id)
    FILE_READS.labels(application=APPLICATION, purpose=stored.purpose.value, access="content").inc()
    logger.info("file.content_read", file_id=str(stored.id), actor_id=str(user.id))
    return content


def content_disposition(stored: StoredFile) -> str:
    """Photos open in the browser; PDFs and text files are saved under their original name."""
    disposition = "inline" if stored.kind.is_image else "attachment"
    ascii_name = stored.file_name.encode("ascii", "ignore").decode().replace('"', "").strip()
    ascii_name = ascii_name or f"dosya{stored.kind.extension}"
    return f"{disposition}; filename=\"{ascii_name}\"; filename*=UTF-8''{quote(stored.file_name, safe='')}"
