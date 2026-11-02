import structlog
from fastapi import APIRouter
from fastapi.responses import JSONResponse
from sqlalchemy import text
from sqlalchemy.exc import SQLAlchemyError

from app.api.dependencies import SessionDep

logger = structlog.get_logger(__name__)

router = APIRouter(prefix="/health", tags=["health"])


@router.get("/live")
def live() -> dict[str, str]:
    return {"status": "ok"}


@router.get("/ready")
def ready(session: SessionDep) -> JSONResponse:
    """Only this service's own database counts: a Keycloak, SQS or SMTP outage keeps the pod in traffic."""
    try:
        session.execute(text("SELECT 1"))
    except SQLAlchemyError as exc:
        logger.warning("health.dependency_unavailable", dependency="database", error=type(exc).__name__)
        return JSONResponse(
            status_code=503, content={"status": "unavailable", "checks": {"database": "unavailable"}}
        )
    return JSONResponse(status_code=200, content={"status": "ok", "checks": {"database": "ok"}})
