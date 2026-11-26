import uuid
from dataclasses import dataclass
from typing import Annotated

from fastapi import APIRouter, Depends, File, Form, Header, UploadFile, status
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
VIEWER_IDS_HEADER = "X-File-Viewer-Ids"


def _view(stored: StoredFile) -> FileView:
    return FileView(
        id=stored.id,
        purpose=stored.purpose,
        content_type=stored.kind.value,
        size_bytes=stored.size_bytes,
        file_name=stored.file_name,
        created_at=stored.created_at,
    )


@dataclass(frozen=True)
class UploadForm:
    """The multipart fields shared by POST /files and PUT /files/{id}."""

    file: UploadFile
    purpose: FilePurpose
    student_ids: list[uuid.UUID]
    teacher_id: uuid.UUID | None
    viewer_ids: list[uuid.UUID]

    def command(self, idempotency_key: str | None = None) -> UploadCommand:
        # Bounded by BodySizeLimitMiddleware, so reading it whole is safe.
        return UploadCommand(
            purpose=self.purpose,
            data=self.file.file.read(),
            file_name=self.file.filename,
            student_ids=self.student_ids,
            teacher_id=self.teacher_id,
            viewer_ids=self.viewer_ids,
            idempotency_key=idempotency_key,
        )


def upload_form(
    file: Annotated[
        UploadFile,
        File(description="JPEG, PNG, WebP or PDF; JPEG or PNG for OMR_SHEET; UTF-8 text for OMR_RESULT"),
    ],
    purpose: Annotated[FilePurpose, Form()],
    student_ids: Annotated[
        list[uuid.UUID] | None,
        Form(alias="studentIds", description="TASK_ATTACHMENT: the students the homework file is for"),
    ] = None,
    teacher_id: Annotated[
        uuid.UUID | None, Form(alias="teacherId", description="SUBMISSION: the teacher who grades it")
    ] = None,
    viewer_ids: Annotated[
        list[uuid.UUID] | None,
        Form(alias="viewerIds", description="MESSAGE_PHOTO: sender and recipient of the chat photo"),
    ] = None,
) -> UploadForm:
    return UploadForm(file, purpose, student_ids or [], teacher_id, viewer_ids or [])


UploadFormDep = Annotated[UploadForm, Depends(upload_form)]


@router.post("", status_code=status.HTTP_201_CREATED, response_model=FileView, response_model_by_alias=True)
def upload(
    session: SessionDep,
    storage: StorageDep,
    settings: SettingsDep,
    user: AnyUser,
    form: UploadFormDep,
    idempotency_key: Annotated[
        str | None, Header(alias="Idempotency-Key", min_length=1, max_length=255)
    ] = None,
) -> FileView:
    """Uploads a file and shares it with the given people; the service picks the id.

    TASK_ATTACHMENT is uploaded by a teacher with `studentIds`, SUBMISSION by a student with
    `teacherId`, OMR_RESULT and OMR_SHEET by the optical reading service without recipients. A
    repeated request with the same `Idempotency-Key` returns the first file instead of a second one.
    """
    return _view(service.upload_file(session, storage, settings, user, form.command(idempotency_key)))


@router.put(
    "/{file_id}",
    status_code=status.HTTP_201_CREATED,
    response_model=FileView,
    response_model_by_alias=True,
    responses={200: {"model": FileView, "description": "The same request was already stored"}},
)
def put(
    file_id: uuid.UUID,
    session: SessionDep,
    storage: StorageDep,
    settings: SettingsDep,
    user: AnyUser,
    form: UploadFormDep,
    response: Response,
) -> FileView:
    """Stores a file under an id the caller picks (the messaging service stores chat photos so).

    MESSAGE_PHOTO takes `viewerIds` (sender and recipient). Sending the same request again returns
    200 with the stored file; a different file under a taken id is 409 FILE_ID_TAKEN.
    """
    stored, created = service.put_file(session, storage, settings, user, file_id, form.command())
    if not created:
        response.status_code = status.HTTP_200_OK
    return _view(stored)


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
    responses={
        200: {
            "content": {"application/octet-stream": {}},
            "description": (
                f"The file's bytes. For MESSAGE_PHOTO the `{VIEWER_IDS_HEADER}` header lists the "
                "people the messaging service stored with the photo (comma separated)."
            ),
        }
    },
)
def get_content(file_id: uuid.UUID, session: SessionDep, storage: StorageDep, user: AnyUser) -> Response:
    """The file's bytes, for services (exam-analysis reads optical results, messaging reads photos)."""
    stored = service.get_readable_file(session, user, file_id)
    content = service.read_content(storage, user, stored)
    headers = {
        **NO_STORE,
        "Content-Disposition": service.content_disposition(stored),
        "X-Content-Type-Options": "nosniff",
    }
    viewers = service.viewer_ids(session, stored)
    if viewers is not None:
        headers[VIEWER_IDS_HEADER] = ",".join(sorted(str(viewer) for viewer in viewers))
    return Response(content=content, media_type=stored.kind.content_type, headers=headers)
