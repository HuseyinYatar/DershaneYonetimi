import time
import uuid

import structlog
from starlette.datastructures import MutableHeaders
from starlette.responses import JSONResponse
from starlette.types import ASGIApp, Message, Receive, Scope, Send

from app.observability.metrics import HTTP_REQUEST_DURATION

logger = structlog.get_logger(__name__)

QUIET_ROUTES = {"/health/live", "/health/ready", "/metrics"}


def route_template(scope: Scope) -> str:
    """Route template (/api/v1/mock-exams/{mock_exam_id}) instead of the raw path, so ids neither
    reach the logs nor explode metric cardinality.

    Built from the matched path and its path parameters, because routers included with a prefix
    do not expose the full template on the route object.
    """
    path: str = scope.get("path", "")
    if path.startswith("/metrics"):
        return "/metrics"
    if scope.get("route") is None:
        return "unmatched"
    names_by_value = {str(value): name for name, value in (scope.get("path_params") or {}).items()}
    return "/".join(
        f"{{{names_by_value[segment]}}}" if segment in names_by_value else segment
        for segment in path.split("/")
    )


class RequestContextMiddleware:
    """Binds request_id to every log line, logs each request once, records its duration and turns
    unexpected exceptions into a logged 500 response."""

    def __init__(self, app: ASGIApp) -> None:
        self.app = app

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return

        headers = dict(scope.get("headers") or [])
        request_id = headers.get(b"x-request-id", b"").decode("latin-1") or uuid.uuid4().hex
        structlog.contextvars.clear_contextvars()
        structlog.contextvars.bind_contextvars(request_id=request_id)
        started = time.perf_counter()
        response_status = 500
        response_started = False

        async def send_with_request_id(message: Message) -> None:
            nonlocal response_status, response_started
            if message["type"] == "http.response.start":
                response_started = True
                response_status = message["status"]
                MutableHeaders(scope=message).append("x-request-id", request_id)
            await send(message)

        try:
            await self.app(scope, receive, send_with_request_id)
        except Exception:
            logger.exception("request.failed", method=scope["method"], route=route_template(scope))
            if not response_started:
                await JSONResponse(
                    status_code=500,
                    content={
                        "code": "INTERNAL_ERROR",
                        "message": "Unexpected error",
                        "details": {"requestId": request_id},
                    },
                )(scope, receive, send_with_request_id)
        finally:
            route = route_template(scope)
            duration = time.perf_counter() - started
            HTTP_REQUEST_DURATION.labels(
                method=scope["method"], route=route, status=str(response_status)
            ).observe(duration)
            if route not in QUIET_ROUTES:
                logger.info(
                    "http.request",
                    method=scope["method"],
                    route=route,
                    status=response_status,
                    duration_ms=round(duration * 1000, 1),
                )
            structlog.contextvars.clear_contextvars()
