from enum import StrEnum


class Role(StrEnum):
    """Keycloak realm roles (values as they appear in realm_access.roles). Never stored."""

    STUDENT = "student"
    TEACHER = "teacher"
    GUARDIAN = "guardian"
    ADMIN = "admin"
    # Given together with admin; manages every branch.
    SUPER_ADMIN = "super-admin"
    # Service accounts of other services (omr-service, exam-analysis-service), not people.
    INTERNAL_SERVICE = "internal-service"


class FilePurpose(StrEnum):
    # A teacher's homework file, shared with the listed students.
    TASK_ATTACHMENT = "TASK_ATTACHMENT"
    # A student's homework submission, shared with one teacher.
    SUBMISSION = "SUBMISSION"
    # Text file the optical reading service produces; exam-analysis scores it.
    OMR_RESULT = "OMR_RESULT"


class FileKind(StrEnum):
    """Detected from the file's first bytes, never from the client's Content-Type."""

    JPEG = "image/jpeg"
    PNG = "image/png"
    WEBP = "image/webp"
    PDF = "application/pdf"
    TEXT = "text/plain"

    @property
    def is_image(self) -> bool:
        return self in {FileKind.JPEG, FileKind.PNG, FileKind.WEBP}

    @property
    def content_type(self) -> str:
        return f"{self.value}; charset=utf-8" if self is FileKind.TEXT else self.value

    @property
    def extension(self) -> str:
        return {
            FileKind.JPEG: ".jpg",
            FileKind.PNG: ".png",
            FileKind.WEBP: ".webp",
            FileKind.PDF: ".pdf",
            FileKind.TEXT: ".txt",
        }[self]
