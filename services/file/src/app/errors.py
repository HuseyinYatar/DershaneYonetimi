"""Domain errors and their user-facing texts.

Every rejected operation raises one of these instead of returning early. The API exception
handler logs each rejection exactly once (code + identifiers, never personal data) and answers
with an RFC 9457 ProblemDetail whose `detail` is Turkish (English with `Accept-Language: en`).
"""

from typing import Any


class DomainError(Exception):
    status_code: int = 400

    # Positional-only, so context may itself carry keys named "code" or "message".
    def __init__(
        self, code: str, message: str, /, *, log_context: dict[str, Any] | None = None, **context: Any
    ) -> None:
        super().__init__(message)
        self.code = code
        # English, for logs and as a fallback when MESSAGES has no entry.
        self.message = message
        # Only identifiers, counts and enum values belong here: the context is logged and returned.
        self.context = context
        # Logged but never returned, e.g. why a file is "not found" (missing or not shared with
        # the caller): the response must not reveal that the file exists.
        self.log_context = log_context or {}


class UnauthorizedError(DomainError):
    status_code = 401


class ForbiddenError(DomainError):
    status_code = 403


class NotFoundError(DomainError):
    status_code = 404


class PayloadTooLargeError(DomainError):
    status_code = 413


class UnsupportedMediaTypeError(DomainError):
    status_code = 415


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
    "PURPOSE_NOT_ALLOWED": (
        "Bu tür dosyayı yükleme yetkiniz yok.",
        "You are not allowed to upload this kind of file.",
    ),
    "RECIPIENTS_REQUIRED": (
        "Dosyanın paylaşılacağı kişiyi seçin.",
        "Choose who the file is shared with.",
    ),
    "RECIPIENTS_NOT_ALLOWED": (
        "Bu tür dosya için paylaşılacak kişi seçilemez.",
        "This kind of file cannot be shared with chosen people.",
    ),
    "TOO_MANY_RECIPIENTS": (
        "Dosya en fazla {maxRecipients} kişiyle paylaşılabilir.",
        "A file can be shared with at most {maxRecipients} people.",
    ),
    "FILE_EMPTY": ("Dosya boş.", "The file is empty."),
    "FILE_TOO_LARGE": (
        "Dosya çok büyük. En fazla {maxMegabytes} MB yükleyebilirsiniz.",
        "The file is too large. The limit is {maxMegabytes} MB.",
    ),
    "UNSUPPORTED_FILE_TYPE": (
        "Bu dosya türü desteklenmiyor.",
        "This file type is not supported.",
    ),
    "INVALID_IMAGE": (
        "Fotoğraf açılamadı. Dosya bozuk veya çok büyük olabilir.",
        "The image could not be opened. It may be damaged or too large.",
    ),
    "FILE_NOT_FOUND": ("Dosya bulunamadı.", "File not found."),
    "IDEMPOTENCY_KEY_REUSED": (
        "Bu istek anahtarı daha önce başka bir dosya için kullanıldı.",
        "This idempotency key was already used for another file.",
    ),
    "FILE_STORAGE_UNAVAILABLE": (
        "Dosya deposuna şu anda ulaşılamıyor. Lütfen biraz sonra tekrar deneyin.",
        "The file storage is unavailable. Please try again later.",
    ),
    "VALIDATION_ERROR": (
        "İstek geçersiz. Lütfen gönderilen alanları kontrol edin.",
        "The request is invalid.",
    ),
    "INTERNAL_ERROR": ("Beklenmeyen bir hata oluştu.", "An unexpected error occurred."),
}


def localized_message(code: str, accept_language: str | None, fallback: str, **context: Any) -> str:
    texts = MESSAGES.get(code)
    if texts is None:
        return fallback
    turkish, english = texts
    text = english if (accept_language or "").lower().startswith("en") else turkish
    try:
        return text.format(**context)
    except KeyError, IndexError:
        return text
