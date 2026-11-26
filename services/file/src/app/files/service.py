"""Use cases: store a file, and hand a file to someone it is shared with."""

import hashlib
import unicodedata
import uuid
from collections.abc import Callable
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from urllib.parse import quote

import structlog
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.auth.keycloak import CurrentUser
from app.config import Settings
from app.errors import (
    BusinessRuleError,
    ConflictError,
    DomainError,
    ForbiddenError,
    NotFoundError,
    PayloadTooLargeError,
)
from app.files.inspection import ImageLimits, detect_kind, sanitize_image
from app.files.rules import RECIPIENT_FIELDS, RULES, PurposeRule
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
    viewer_ids: list[uuid.UUID]
    idempotency_key: str | None


@dataclass(frozen=True)
class DownloadUrl:
    url: str
    expires_at: datetime


@dataclass(frozen=True)
class _CheckedUpload:
    """What every upload path needs after the request passed the purpose's rules."""

    rule: PurposeRule
    recipients: set[uuid.UUID]
    source_sha256: str


def upload_file(
    session: Session, storage: FileStorage, settings: Settings, user: CurrentUser, command: UploadCommand
) -> StoredFile:
    """POST /files: the service picks the id. An Idempotency-Key makes a repeated request safe."""
    checked = _check(user, settings, command)
    key = command.idempotency_key
    if key is not None:
        existing = _find_by_key(session, user.id, key)
        if existing is not None:
            return _replay(session, existing, user, command.purpose, checked, conflict=_key_reused)
    try:
        return _store(session, storage, settings, user, command, checked, uuid.uuid4())
    except IntegrityError:
        # Two requests with the same Idempotency-Key at the same time: the other one won.
        existing = _find_by_key(session, user.id, key) if key is not None else None
        if existing is None:
            raise
        return _replay(session, existing, user, command.purpose, checked, conflict=_key_reused)


def put_file(
    session: Session,
    storage: FileStorage,
    settings: Settings,
    user: CurrentUser,
    file_id: uuid.UUID,
    command: UploadCommand,
) -> tuple[StoredFile, bool]:
    """PUT /files/{id}: the caller picks the id (the messaging service stores chat photos this way).

    Repeating the same request returns the stored file instead of a second copy. Returns the file
    and whether it was created by this call.
    """
    checked = _check(user, settings, command)
    existing = session.get(StoredFile, file_id)
    if existing is not None:
        return _replay(session, existing, user, command.purpose, checked, conflict=_id_taken), False
    try:
        return _store(session, storage, settings, user, command, checked, file_id), True
    except IntegrityError:
        # The same id was stored by a request running at the same time.
        existing = session.get(StoredFile, file_id)
        if existing is None:
            raise
        return _replay(session, existing, user, command.purpose, checked, conflict=_id_taken), False


def _check(user: CurrentUser, settings: Settings, command: UploadCommand) -> _CheckedUpload:
    rule = RULES[command.purpose]
    if not _may_upload(user, rule):
        raise ForbiddenError(
            "PURPOSE_NOT_ALLOWED",
            "The caller may not upload files of this purpose",
            purpose=command.purpose.value,
            log_context={"actor_id": str(user.id), "client_id": user.client_id},
        )
    recipients = _recipients(rule, command, settings.max_recipients, owner_id=user.id)
    if not command.data:
        raise BusinessRuleError("FILE_EMPTY", "The uploaded file is empty")
    return _CheckedUpload(rule, recipients, hashlib.sha256(command.data).hexdigest())


def _may_upload(user: CurrentUser, rule: PurposeRule) -> bool:
    return user.has_any_role(*rule.uploader_roles) or any(
        user.is_service(client) for client in rule.uploader_clients
    )


def _store(
    session: Session,
    storage: FileStorage,
    settings: Settings,
    user: CurrentUser,
    command: UploadCommand,
    checked: _CheckedUpload,
    file_id: uuid.UUID,
) -> StoredFile:
    """Checks the content, writes the row and the S3 object, commits.

    The row is flushed before the object is written: a request that collides on the file id or the
    Idempotency-Key fails at the flush, before it can overwrite another file's content in S3. The
    commit comes last, so a failed S3 write leaves no row behind. Raises IntegrityError (after a
    rollback) on such a collision.
    """
    kind = detect_kind(command.data, checked.rule.kinds)
    limit = settings.max_image_bytes if kind.is_image else settings.max_document_bytes
    if len(command.data) > limit:
        raise PayloadTooLargeError(
            "FILE_TOO_LARGE", "The file exceeds the size limit", maxMegabytes=limit // MIB
        )
    content = command.data
    if kind.is_image:
        max_edge = settings.image_max_edge_px if checked.rule.shrink_images else None
        content = sanitize_image(command.data, kind, ImageLimits(settings.image_max_pixels, max_edge))

    stored = StoredFile(
        id=file_id,
        purpose=command.purpose,
        owner_id=user.id,
        kind=kind,
        size_bytes=len(content),
        file_name=_clean_file_name(command.file_name, kind),
        source_sha256=checked.source_sha256,
        idempotency_key=command.idempotency_key,
    )
    try:
        session.add(stored)
        # The models have no relationship(), so the file row is flushed before its access rows.
        session.flush()
        session.add_all(FileAccess(file_id=file_id, user_id=user_id) for user_id in checked.recipients)
        session.flush()
    except IntegrityError:
        session.rollback()
        raise
    # A failure here raises; the request's session is rolled back, so no row is left without content.
    storage.put(file_id, content, kind.content_type)
    session.commit()

    FILE_UPLOADS.labels(application=APPLICATION, purpose=stored.purpose.value, outcome="stored").inc()
    FILE_UPLOAD_BYTES.labels(application=APPLICATION, purpose=stored.purpose.value).observe(stored.size_bytes)
    logger.info(
        "file.uploaded",
        file_id=str(stored.id),
        owner_id=str(user.id),
        purpose=stored.purpose.value,
        content_type=kind.value,
        size_bytes=stored.size_bytes,
        recipient_count=len(checked.recipients),
    )
    return stored


def _recipients(
    rule: PurposeRule, command: UploadCommand, max_recipients: int, *, owner_id: uuid.UUID
) -> set[uuid.UUID]:
    """The people the file is shared with, from the one request field the purpose expects."""
    given = {
        "studentIds": set(command.student_ids),
        "teacherId": {command.teacher_id} if command.teacher_id is not None else set(),
        "viewerIds": set(command.viewer_ids),
    }
    expected_field = RECIPIENT_FIELDS.get(rule.recipients)
    wrong_fields = sorted(field for field, ids in given.items() if ids and field != expected_field)
    if wrong_fields:
        raise BusinessRuleError(
            "RECIPIENTS_NOT_ALLOWED",
            "This purpose takes no such recipient field",
            purpose=command.purpose.value,
            expectedField=expected_field,
            log_context={"fields": wrong_fields},
        )
    recipients = given[expected_field] if expected_field is not None else set()
    if expected_field is not None and not recipients:
        raise BusinessRuleError(
            "RECIPIENTS_REQUIRED", "The file must be shared with someone", expectedField=expected_field
        )
    if len(recipients) > max_recipients:
        raise BusinessRuleError("TOO_MANY_RECIPIENTS", "Too many recipients", maxRecipients=max_recipients)
    # The owner reads the file as its owner; a row for them would change nothing.
    recipients.discard(owner_id)
    return recipients


def _find_by_key(session: Session, owner_id: uuid.UUID, key: str) -> StoredFile | None:
    return session.scalars(
        select(StoredFile).where(StoredFile.owner_id == owner_id, StoredFile.idempotency_key == key)
    ).one_or_none()


def _key_reused(existing: StoredFile) -> DomainError:
    return BusinessRuleError(
        "IDEMPOTENCY_KEY_REUSED",
        "The Idempotency-Key was used for a different upload",
        log_context={"file_id": str(existing.id)},
    )


def _id_taken(existing: StoredFile) -> DomainError:
    return ConflictError("FILE_ID_TAKEN", "The file id is used by another file", file_id=str(existing.id))


def _replay(
    session: Session,
    existing: StoredFile,
    user: CurrentUser,
    purpose: FilePurpose,
    checked: _CheckedUpload,
    *,
    conflict: Callable[[StoredFile], DomainError],
) -> StoredFile:
    """A repeated request is answered with the file it stored; anything else is a conflict."""
    same_request = (
        existing.owner_id == user.id
        and existing.purpose is purpose
        and existing.source_sha256 == checked.source_sha256
        and shared_with(session, existing.id) == checked.recipients
    )
    if not same_request:
        raise conflict(existing)
    FILE_UPLOADS.labels(application=APPLICATION, purpose=purpose.value, outcome="replayed").inc()
    logger.info("file.upload_replayed", file_id=str(existing.id), owner_id=str(existing.owner_id))
    return existing


def shared_with(session: Session, file_id: uuid.UUID) -> set[uuid.UUID]:
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
            log_context={"reason": "not_shared", "actor_id": str(user.id), "client_id": user.client_id},
        )
    return stored


def _can_read(session: Session, user: CurrentUser, stored: StoredFile) -> bool:
    rule = RULES[stored.purpose]
    if stored.owner_id == user.id:
        return True
    if user.has_any_role(*rule.reader_roles):
        return True
    if any(user.is_service(client) for client in rule.reader_clients):
        return True
    if not rule.shared_list_grants_access:
        return False
    return session.get(FileAccess, {"file_id": stored.id, "user_id": user.id}) is not None


def viewer_ids(session: Session, stored: StoredFile) -> set[uuid.UUID] | None:
    """The viewer list a service stored with the file (chat photos); None for other purposes.

    Only the calling service gets it: for homework files it would list a classroom's students.
    """
    if RULES[stored.purpose].shared_list_grants_access:
        return None
    viewers = shared_with(session, stored.id)
    return viewers or None


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
    """Photos open in the browser; PDFs, text files and homework files are saved under their name."""
    opens_in_browser = stored.kind.is_image and not RULES[stored.purpose].always_download
    disposition = "inline" if opens_in_browser else "attachment"
    ascii_name = stored.file_name.encode("ascii", "ignore").decode().replace('"', "").strip()
    ascii_name = ascii_name or f"dosya{stored.kind.extension}"
    return f"{disposition}; filename=\"{ascii_name}\"; filename*=UTF-8''{quote(stored.file_name, safe='')}"
