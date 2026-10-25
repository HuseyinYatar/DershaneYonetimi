from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

import structlog
from fastapi import FastAPI
from prometheus_client import make_asgi_app

from app.api.exception_handlers import register_exception_handlers
from app.api.middleware import RequestContextMiddleware
from app.api.routers import admin_replicas, catalog, health, import_jobs, mock_exams, settings, students
from app.config import Settings, get_settings
from app.db import get_engine
from app.observability.logging import configure_logging
from app.observability.tracing import configure_tracing

logger = structlog.get_logger(__name__)

API_PREFIX = "/api/v1"


def create_app(app_settings: Settings | None = None) -> FastAPI:
    """Application factory: `uvicorn app.main:create_app --factory`."""
    app_settings = app_settings or get_settings()
    configure_logging(app_settings.log_level, app_settings.service_name, app_settings.environment)

    @asynccontextmanager
    async def lifespan(_: FastAPI) -> AsyncIterator[None]:
        logger.info("service.started", environment=app_settings.environment)
        yield
        logger.info("service.stopping")

    app = FastAPI(
        title="exam-analysis",
        description="Mock exam scoring, topic analysis and progress reports",
        version="0.1.0",
        lifespan=lifespan,
    )
    app.add_middleware(RequestContextMiddleware)
    register_exception_handlers(app)

    app.include_router(health.router)
    for module in (catalog, mock_exams, import_jobs, students, settings, admin_replicas):
        app.include_router(module.router, prefix=API_PREFIX)
    app.mount("/metrics", make_asgi_app())

    configure_tracing(app_settings, app=app, engine=get_engine())
    return app
