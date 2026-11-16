import uuid
from typing import Annotated

from fastapi import APIRouter, File, Form, Header, UploadFile, status
from fastapi.responses import Response

from app.api.dependencies import SessionDep, SettingsDep, StorageDep
from app.auth.dependencies import AnyUser
from app.files import service
from app.files.service import UploadCommand
from app.models import StoredFile
from app.models.enums import FilePurpose
from app.schemas import DownloadUrlView, FileView

router = APIRouter(prefix="/files", tags=["files"])

NO_STORE = {"Cache-Control": "no-store"}


def _view(stored: StoredFile) -> FileView:
    return FileView(
        id=stored.id,
        purpose=stored.purpose,
        content_type=stored.kind.value,
        size_bytes=stored.size_bytes,
        file_name=stored.file_name,
        created_at=stored.created_at,
    )


@router.post("", status_code=status.HTTP_201_CREATED, response_model=FileView, response_model_by_alias=True)
def upload(
    session: SessionDep,
    storage: StorageDep,
    settings: SettingsDep,
    user: AnyUser,
    file: Annotated[UploadFile, File(description="JPEG, PNG, WebP or PDF; plain UTF-8 text for OMR_RESULT")],
    purpose: Annotated[FilePurpose, Form()],
    student_ids: Annotated[
        list[uuid.UUID] | None,
        Form(alias="studentIds", description="TASK_ATTACHMENT: the students the homework file is for"),
    ] = None,
    teacher_id: Annotated[
        uuid.UUID | None, Form(alias="teacherId", description="SUBMISSION: the teacher who grades it")
    ] = None,
    idempotency_key: Annotated[
        str | None, Header(alias="Idempotency-Key", min_length=1, max_length=255)
    ] = None,
) -> FileView:
    """Uploads a file and shares it with the given people.

    TASK_ATTACHMENT is uploaded by a teacher with `studentIds`, SUBMISSION by a student with
    `teacherId`, OMR_RESULT by the optical reading service without recipients. A repeated request
    with the same `Idempotency-Key` returns the first file instead of storing a second one.
    """
    # Bounded by BodySizeLimitMiddleware, so reading it whole is safe.
    data = file.file.read()
    command = UploadCommand(
        purpose=purpose,
        data=data,
        file_name=file.filename,
        student_ids=student_ids or [],
        teacher_id=teacher_id,
        idempotency_key=idempotency_key,
    )
    return _view(service.upload_file(session, storage, settings, user, command))


@router.get("/{file_id}", response_model=FileView, response_model_by_alias=True)
def get_file(file_id: uuid.UUID, session: SessionDep, user: AnyUser) -> FileView:
    """Name, type and size of a file shared with the caller."""
    return _view(service.get_readable_file(session, user, file_id))


@router.get("/{file_id}/download-url", response_model=DownloadUrlView, response_model_by_alias=True)
def get_download_url(
    file_id: uuid.UUID,
    session: SessionDep,
    storage: StorageDep,
    settings: SettingsDep,
    user: AnyUser,
    response: Response,
) -> DownloadUrlView:
    """Short-lived S3 address for the browser (`<img src>`, download link)."""
    stored = service.get_readable_file(session, user, file_id)
    download = service.issue_download_url(storage, settings, user, stored)
    response.headers.update(NO_STORE)
    return DownloadUrlView(url=download.url, expires_at=download.expires_at)


@router.get(
    "/{file_id}/content",
    response_class=Response,
    responses={200: {"content": {"application/octet-stream": {}}, "description": "The file's bytes"}},
)
def get_content(file_id: uuid.UUID, session: SessionDep, storage: StorageDep, user: AnyUser) -> Response:
    """The file's bytes, for services (exam-analysis reads optical results this way)."""
    stored = service.get_readable_file(session, user, file_id)
    content = service.read_content(storage, user, stored)
    return Response(
        content=content,
        media_type=stored.kind.content_type,
        headers={
            **NO_STORE,
            "Content-Disposition": service.content_disposition(stored),
            "X-Content-Type-Options": "nosniff",
        },
    )
