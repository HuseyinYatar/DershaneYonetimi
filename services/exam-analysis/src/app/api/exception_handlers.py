"""Single place where rejected requests are logged: code and identifiers, never request bodies."""

from typing import Any

import structlog
from fastapi import FastAPI, Request
from fastapi.encoders import jsonable_encoder
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from sqlalchemy.exc import IntegrityError

from app.api.middleware import route_template
from app.errors import DomainError
from app.observability.metrics import DOMAIN_ERRORS

logger = structlog.get_logger(__name__)


def _route(request: Request) -> str:
    return route_template(request.scope)


async def handle_domain_error(request: Request, exc: Exception) -> JSONResponse:
    assert isinstance(exc, DomainError)
    DOMAIN_ERRORS.labels(code=exc.code).inc()
    log = logger.error if exc.status_code >= 500 else logger.warning
    details = jsonable_encoder(exc.context)
    log(
        "request.rejected", code=exc.code, http_status=exc.status_code, route=_route(request), context=details
    )
    return JSONResponse(
        status_code=exc.status_code, content={"code": exc.code, "message": exc.message, "details": details}
    )


async def handle_validation_error(request: Request, exc: Exception) -> JSONResponse:
    assert isinstance(exc, RequestValidationError)
    # Submitted values may contain personal data: only locations and error types are logged/returned.
    errors: list[dict[str, Any]] = [
        {"loc": list(error.get("loc", ())), "type": error.get("type"), "msg": error.get("msg")}
        for error in exc.errors()
    ]
    DOMAIN_ERRORS.labels(code="VALIDATION_ERROR").inc()
    logger.warning(
        "request.invalid",
        route=_route(request),
        errors=[{"loc": error["loc"], "type": error["type"]} for error in errors],
    )
    return JSONResponse(
        status_code=422,
        content={
            "code": "VALIDATION_ERROR",
            "message": "Request validation failed",
            "details": {"errors": errors},
        },
    )


async def handle_integrity_error(request: Request, exc: Exception) -> JSONResponse:
    """Last line of defence for races the use cases' own checks cannot see (two concurrent requests)."""
    assert isinstance(exc, IntegrityError)
    constraint = getattr(getattr(exc.orig, "diag", None), "constraint_name", None)
    DOMAIN_ERRORS.labels(code="CONSTRAINT_VIOLATION").inc()
    logger.warning(
        "request.rejected",
        code="CONSTRAINT_VIOLATION",
        http_status=409,
        route=_route(request),
        constraint=constraint,
    )
    return JSONResponse(
        status_code=409,
        content={
            "code": "CONSTRAINT_VIOLATION",
            "message": "The change conflicts with existing data",
            "details": {"constraint": constraint},
        },
    )


def register_exception_handlers(app: FastAPI) -> None:
    app.add_exception_handler(DomainError, handle_domain_error)
    app.add_exception_handler(RequestValidationError, handle_validation_error)
    app.add_exception_handler(IntegrityError, handle_integrity_error)
