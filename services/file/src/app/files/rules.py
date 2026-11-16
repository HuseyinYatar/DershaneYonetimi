"""Who may upload and who may read each kind of file.

The file service does not know tasks, classrooms or exams. The uploader says who the file is
shared with (`studentIds` / `teacherId`); the service stores that list and checks it on every read.
"""

from dataclasses import dataclass
from enum import StrEnum

from app.models.enums import FileKind, FilePurpose, Role


class Recipients(StrEnum):
    NONE = "NONE"
    # One or more students (a classroom's homework lists every student of the classroom).
    STUDENTS = "STUDENTS"
    # Exactly one teacher.
    TEACHER = "TEACHER"


@dataclass(frozen=True)
class PurposeRule:
    uploader_roles: frozenset[Role]
    kinds: frozenset[FileKind]
    recipients: Recipients
    # Read every file of this purpose, no matter who uploaded it or whom it is shared with.
    reader_roles: frozenset[Role] = frozenset()


_DOCUMENTS = frozenset({FileKind.JPEG, FileKind.PNG, FileKind.WEBP, FileKind.PDF})

RULES: dict[FilePurpose, PurposeRule] = {
    FilePurpose.TASK_ATTACHMENT: PurposeRule(
        uploader_roles=frozenset({Role.TEACHER}), kinds=_DOCUMENTS, recipients=Recipients.STUDENTS
    ),
    FilePurpose.SUBMISSION: PurposeRule(
        uploader_roles=frozenset({Role.STUDENT}), kinds=_DOCUMENTS, recipients=Recipients.TEACHER
    ),
    FilePurpose.OMR_RESULT: PurposeRule(
        uploader_roles=frozenset({Role.INTERNAL_SERVICE}),
        kinds=frozenset({FileKind.TEXT}),
        recipients=Recipients.NONE,
        reader_roles=frozenset({Role.INTERNAL_SERVICE, Role.SUPER_ADMIN}),
    ),
}
