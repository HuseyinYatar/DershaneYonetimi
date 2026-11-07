"""RFC 9457 ProblemDetail responses: English errorCode, Turkish detail (English with Accept-Language: en)."""

from http import HTTPStatus
from typing import Any

from starlette.responses import JSONResponse

from app.errors import localized_message


def problem_response(
    status: int, code: str, fallback_message: str, accept_language: str | None, **extra: Any
) -> JSONResponse:
    return JSONResponse(
        status_code=status,
        media_type="application/problem+json",
        content={
            "type": "about:blank",
            "title": HTTPStatus(status).phrase,
            "status": status,
            "detail": localized_message(code, accept_language, fallback_message),
            "errorCode": code,
            **extra,
        },
    )
