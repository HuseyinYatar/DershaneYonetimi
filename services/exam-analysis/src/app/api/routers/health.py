import redis
import structlog
from fastapi import APIRouter
from fastapi.responses import JSONResponse
from sqlalchemy import text
from sqlalchemy.exc import SQLAlchemyError

from app.api.dependencies import SessionDep, SettingsDep

logger = structlog.get_logger(__name__)

router = APIRouter(prefix="/health", tags=["health"])


@router.get("/live")
def live() -> dict[str, str]:
    return {"status": "ok"}


@router.get("/ready")
def ready(session: SessionDep, settings: SettingsDep) -> JSONResponse:
    """PostgreSQL is required; Redis is optional (the service falls back to the database)."""
    checks: dict[str, str] = {}
    try:
        session.execute(text("SELECT 1"))
        checks["database"] = "ok"
    except SQLAlchemyError as exc:
        checks["database"] = "unavailable"
        logger.warning("health.dependency_unavailable", dependency="database", error=type(exc).__name__)

    if settings.redis_url is None:
        checks["cache"] = "disabled"
    else:
        try:
            redis.Redis.from_url(settings.redis_url, socket_timeout=0.5).ping()
            checks["cache"] = "ok"
        except redis.RedisError as exc:
            checks["cache"] = "degraded"
            logger.warning("health.dependency_unavailable", dependency="cache", error=type(exc).__name__)

    is_ready = checks["database"] == "ok"
    return JSONResponse(
        status_code=200 if is_ready else 503,
        content={"status": "ok" if is_ready else "unavailable", "checks": checks},
    )
