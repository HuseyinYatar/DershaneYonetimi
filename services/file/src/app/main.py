from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

import structlog
from fastapi import FastAPI
from prometheus_client import make_asgi_app

from app.api.exception_handlers import register_exception_handlers
from app.api.middleware import BodySizeLimitMiddleware, RequestContextMiddleware
from app.api.openapi import install_keycloak_login, swagger_ui_oauth
from app.api.routers import files, health
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

    if not app_settings.swagger_enabled:
        logger.info("swagger.disabled", environment=app_settings.environment)
    app = FastAPI(
        title="file",
        description="Homework attachments, submission files and optical reading results",
        version="0.1.0",
        lifespan=lifespan,
        docs_url="/docs" if app_settings.swagger_enabled else None,
        redoc_url=None,
        openapi_url="/openapi.json" if app_settings.swagger_enabled else None,
        swagger_ui_init_oauth=swagger_ui_oauth(app_settings),
    )
    # Added last = outermost: the request context wraps the size limit, so a 413 is logged too.
    app.add_middleware(
        BodySizeLimitMiddleware,
        max_bytes=app_settings.max_request_bytes,
        max_file_megabytes=app_settings.max_file_megabytes,
    )
    app.add_middleware(RequestContextMiddleware)
    register_exception_handlers(app)

    app.include_router(health.router)
    app.include_router(files.router, prefix=API_PREFIX)
    app.mount("/metrics", make_asgi_app())
    install_keycloak_login(app, app_settings)

    configure_tracing(app_settings, app=app, engine=get_engine())
    return app
