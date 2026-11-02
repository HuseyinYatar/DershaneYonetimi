"""Single place where rejected requests are logged: code and identifiers, never request bodies."""

import structlog
from fastapi import FastAPI, Request
from fastapi.encoders import jsonable_encoder
from fastapi.exceptions import RequestValidationError
from starlette.responses import Response

from app.api.middleware import route_template
from app.api.problem import problem_response
from app.errors import DomainError
from app.observability.metrics import DOMAIN_ERRORS

logger = structlog.get_logger(__name__)


async def handle_domain_error(request: Request, exc: Exception) -> Response:
    assert isinstance(exc, DomainError)
    DOMAIN_ERRORS.labels(code=exc.code).inc()
    log = logger.error if exc.status_code >= 500 else logger.warning
    context = jsonable_encoder(exc.context)
    log(
        "request.rejected",
        code=exc.code,
        http_status=exc.status_code,
        route=route_template(request.scope),
        context=context,
    )
    return problem_response(
        exc.status_code, exc.code, exc.message, request.headers.get("accept-language"), **context
    )


async def handle_validation_error(request: Request, exc: Exception) -> Response:
    assert isinstance(exc, RequestValidationError)
    # Submitted values may contain personal data: only locations and error types are logged/returned.
    errors = [{"loc": list(error.get("loc", ())), "type": error.get("type")} for error in exc.errors()]
    DOMAIN_ERRORS.labels(code="VALIDATION_ERROR").inc()
    logger.warning("request.invalid", route=route_template(request.scope), errors=errors)
    return problem_response(
        422,
        "VALIDATION_ERROR",
        "Request validation failed",
        request.headers.get("accept-language"),
        errors=errors,
    )


def register_exception_handlers(app: FastAPI) -> None:
    app.add_exception_handler(DomainError, handle_domain_error)
    app.add_exception_handler(RequestValidationError, handle_validation_error)
