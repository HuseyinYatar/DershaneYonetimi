"""Who may upload and who may read each kind of file.

The file service does not know tasks, classrooms, exams or chats. The uploader says who the file is
shared with (`studentIds` / `teacherId` / `viewerIds`); the service stores that list.

People are recognised by their realm role (teacher, student, super-admin). Services are recognised
by their Keycloak client (the token's `azp`) together with the `internal-service` role, so one
service cannot read another service's files.
"""

from dataclasses import dataclass
from enum import StrEnum

from app.models.enums import FileKind, FilePurpose, Role

# Keycloak clients of the services that call the file service (local/keycloak/dershane-realm.json).
OMR_SERVICE = "omr-service"
EXAM_ANALYSIS_SERVICE = "exam-analysis-service"
MESSAGING_SERVICE = "messaging-service"


class Recipients(StrEnum):
    NONE = "NONE"
    # One or more students (a classroom's homework lists every student of the classroom).
    STUDENTS = "STUDENTS"
    # Exactly one teacher.
    TEACHER = "TEACHER"
    # People the calling service names. The list is stored and handed back to that service, which
    # decides who sees the file; the people themselves cannot fetch it from the file service.
    VIEWERS = "VIEWERS"


# The request field that carries each kind of recipient list.
RECIPIENT_FIELDS = {
    Recipients.STUDENTS: "studentIds",
    Recipients.TEACHER: "teacherId",
    Recipients.VIEWERS: "viewerIds",
}


@dataclass(frozen=True)
class PurposeRule:
    kinds: frozenset[FileKind]
    recipients: Recipients
    # People who may upload, by role.
    uploader_roles: frozenset[Role] = frozenset()
    # Services that may upload, by Keycloak client.
    uploader_clients: frozenset[str] = frozenset()
    # People who read every file of this purpose, no matter who uploaded it or whom it is shared with.
    reader_roles: frozenset[Role] = frozenset()
    # Services that read every file of this purpose.
    reader_clients: frozenset[str] = frozenset()
    # Photos are scaled down to image_max_edge_px. False keeps the full size (answer sheets must stay
    # readable); the metadata (EXIF, GPS position) is dropped either way.
    shrink_images: bool = True
    # True: the browser saves the file even when it is a photo, instead of opening it.
    always_download: bool = False

    @property
    def shared_list_grants_access(self) -> bool:
        """Whether the people in the stored list may fetch the file themselves."""
        return self.recipients in {Recipients.STUDENTS, Recipients.TEACHER}


_DOCUMENTS = frozenset({FileKind.JPEG, FileKind.PNG, FileKind.WEBP, FileKind.PDF})
_PHOTOS = frozenset({FileKind.JPEG, FileKind.PNG, FileKind.WEBP})

RULES: dict[FilePurpose, PurposeRule] = {
    FilePurpose.TASK_ATTACHMENT: PurposeRule(
        kinds=_DOCUMENTS,
        recipients=Recipients.STUDENTS,
        uploader_roles=frozenset({Role.TEACHER}),
        always_download=True,
    ),
    FilePurpose.SUBMISSION: PurposeRule(
        kinds=_DOCUMENTS,
        recipients=Recipients.TEACHER,
        uploader_roles=frozenset({Role.STUDENT}),
        always_download=True,
    ),
    FilePurpose.OMR_RESULT: PurposeRule(
        kinds=frozenset({FileKind.TEXT}),
        recipients=Recipients.NONE,
        uploader_clients=frozenset({OMR_SERVICE}),
        reader_roles=frozenset({Role.SUPER_ADMIN}),
        reader_clients=frozenset({EXAM_ANALYSIS_SERVICE}),
    ),
    FilePurpose.OMR_SHEET: PurposeRule(
        kinds=frozenset({FileKind.JPEG, FileKind.PNG}),
        recipients=Recipients.NONE,
        uploader_clients=frozenset({OMR_SERVICE}),
        reader_roles=frozenset({Role.SUPER_ADMIN}),
        shrink_images=False,
    ),
    FilePurpose.MESSAGE_PHOTO: PurposeRule(
        kinds=_PHOTOS,
        recipients=Recipients.VIEWERS,
        uploader_clients=frozenset({MESSAGING_SERVICE}),
        reader_clients=frozenset({MESSAGING_SERVICE}),
    ),
}
