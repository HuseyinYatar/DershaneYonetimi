"""Domain errors.

Every rejected operation raises one of these instead of returning early. The API exception
handler logs each rejection exactly once (code + identifiers, never personal data) and maps it
to an HTTP status. The worker logs them when a job fails.
"""

from typing import Any


class DomainError(Exception):
    status_code: int = 400

    # Positional-only, so context may itself carry keys named "code" or "message".
    def __init__(self, code: str, message: str, /, **context: Any) -> None:
        super().__init__(message)
        self.code = code
        self.message = message
        # Only identifiers, counts and enum values belong here: the context is logged.
        self.context = context


class UnauthorizedError(DomainError):
    status_code = 401


class ForbiddenError(DomainError):
    status_code = 403


class NotFoundError(DomainError):
    status_code = 404


class ConflictError(DomainError):
    """The resource is in a state that does not allow the operation."""

    status_code = 409


class BusinessRuleError(DomainError):
    """The request is well-formed but violates a domain rule."""

    status_code = 422


class DependencyUnavailableError(DomainError):
    status_code = 503
