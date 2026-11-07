"""Domain errors and their user-facing texts.

Every rejected operation raises one of these instead of returning early. The API exception
handler logs each rejection exactly once (code + identifiers, never personal data) and answers
with an RFC 9457 ProblemDetail whose `detail` is Turkish (English with `Accept-Language: en`).
"""

from typing import Any


class DomainError(Exception):
    status_code: int = 400

    # Positional-only, so context may itself carry keys named "code" or "message".
    def __init__(self, code: str, message: str, /, **context: Any) -> None:
        super().__init__(message)
        self.code = code
        # English, for logs and as a fallback when MESSAGES has no entry.
        self.message = message
        # Only identifiers, counts and enum values belong here: the context is logged.
        self.context = context


class UnauthorizedError(DomainError):
    status_code = 401


class NotFoundError(DomainError):
    status_code = 404


class BusinessRuleError(DomainError):
    """The request is well-formed but violates a domain rule."""

    status_code = 422


class DependencyUnavailableError(DomainError):
    status_code = 503


# errorCode -> (Turkish, English). Turkish is the default language of the API.
MESSAGES: dict[str, tuple[str, str]] = {
    "TOKEN_MISSING": ("Bu işlem için giriş yapmanız gerekiyor.", "You need to log in for this operation."),
    "TOKEN_INVALID": (
        "Oturum bilgisi geçersiz. Lütfen tekrar giriş yapın.",
        "The session is invalid. Please log in again.",
    ),
    "TOKEN_EXPIRED": (
        "Oturumunuzun süresi doldu. Lütfen tekrar giriş yapın.",
        "Your session has expired. Please log in again.",
    ),
    "IDENTITY_PROVIDER_UNAVAILABLE": (
        "Kimlik doğrulama servisine şu anda ulaşılamıyor. Lütfen biraz sonra tekrar deneyin.",
        "The identity service is unavailable. Please try again later.",
    ),
    "NOTIFICATION_NOT_FOUND": ("Bildirim bulunamadı.", "Notification not found."),
    "CHANNEL_NOT_CONFIGURABLE": (
        "Uygulama içi bildirimler kapatılamaz; yalnızca e-posta ve SMS kapatılabilir.",
        "In-app notifications cannot be turned off; only e-mail and SMS can.",
    ),
    "EVENT_TYPE_NOT_CONFIGURABLE": (
        "Bu bildirim türü için tercih ayarlanamaz.",
        "Preferences cannot be set for this notification type.",
    ),
    "VALIDATION_ERROR": (
        "İstek geçersiz. Lütfen gönderilen alanları kontrol edin.",
        "The request is invalid.",
    ),
    "INTERNAL_ERROR": ("Beklenmeyen bir hata oluştu.", "An unexpected error occurred."),
}


def localized_message(code: str, accept_language: str | None, fallback: str) -> str:
    texts = MESSAGES.get(code)
    if texts is None:
        return fallback
    turkish, english = texts
    return english if (accept_language or "").lower().startswith("en") else turkish
