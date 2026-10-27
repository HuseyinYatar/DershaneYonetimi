from enum import StrEnum

# Member values equal member names: SQLAlchemy stores names, the API and events expose values.


class ExamStatus(StrEnum):
    DRAFT = "DRAFT"
    KEY_READY = "KEY_READY"
    PROCESSED = "PROCESSED"
    PUBLISHED = "PUBLISHED"


class JobStatus(StrEnum):
    QUEUED = "QUEUED"
    PROCESSING = "PROCESSING"
    COMPLETED = "COMPLETED"
    FAILED = "FAILED"


class AnswerStatus(StrEnum):
    CORRECT = "CORRECT"
    WRONG = "WRONG"
    BLANK = "BLANK"
    # Cancelled questions are left out of correct/wrong/blank counts and topic totals.
    CANCELLED = "CANCELLED"


class ScoringSource(StrEnum):
    INSTITUTIONAL = "INSTITUTIONAL"
    NATIONAL = "NATIONAL"


class MatchStatus(StrEnum):
    PENDING = "PENDING"
    MATCHED = "MATCHED"
    IGNORED = "IGNORED"


class UnmatchedReason(StrEnum):
    STUDENT_NOT_FOUND = "STUDENT_NOT_FOUND"
    DUPLICATE_STUDENT = "DUPLICATE_STUDENT"
    INVALID_BOOKLET = "INVALID_BOOKLET"
    MALFORMED_LINE = "MALFORMED_LINE"


class ImportSource(StrEnum):
    FILE_UPLOAD = "FILE_UPLOAD"
    OMR = "OMR"


class Role(StrEnum):
    """Keycloak realm roles (values as they appear in realm_access.roles). Never stored."""

    STUDENT = "student"
    TEACHER = "teacher"
    GUARDIAN = "guardian"
    ADMIN = "admin"
    # Given together with admin; manages every branch.
    SUPER_ADMIN = "super-admin"
