import contextlib
import time
import uuid

import structlog
from starlette.datastructures import MutableHeaders
from starlette.types import ASGIApp, Message, Receive, Scope, Send

from app.api.problem import problem_response
from app.observability.metrics import APPLICATION, DOMAIN_ERRORS, HTTP_REQUEST_DURATION

logger = structlog.get_logger(__name__)

QUIET_ROUTES = {"/health/live", "/health/ready", "/metrics"}


def route_template(scope: Scope) -> str:
    """Route template (/api/v1/files/{file_id}) instead of the raw path, so ids neither reach the
    logs nor explode metric cardinality.

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


def _accept_language(scope: Scope) -> str | None:
    headers = dict(scope.get("headers") or [])
    return headers.get(b"accept-language", b"").decode("latin-1") or None


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
                await problem_response(
                    500, "INTERNAL_ERROR", "Unexpected error", _accept_language(scope), requestId=request_id
                )(scope, receive, send_with_request_id)
        finally:
            route = route_template(scope)
            duration = time.perf_counter() - started
            HTTP_REQUEST_DURATION.labels(
                application=APPLICATION, method=scope["method"], route=route, status=str(response_status)
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


class _BodyTooLargeError(Exception):
    pass


class BodySizeLimitMiddleware:
    """Rejects a request body larger than `max_bytes` with 413 while it is still arriving.

    FastAPI parses a multipart upload completely (spooling it to disk) before the endpoint runs,
    so the limit cannot wait for the endpoint. A declared Content-Length is checked up front; the
    bytes are counted as well, because a chunked request declares none. The exact per-type limit
    (photo 10 MB, PDF 20 MB) is checked afterwards in the upload use case.
    """

    def __init__(self, app: ASGIApp, max_bytes: int, max_file_megabytes: int) -> None:
        self.app = app
        self.max_bytes = max_bytes
        # Shown to the user: the largest file accepted, not the raw body limit.
        self.max_file_megabytes = max_file_megabytes

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return

        declared = dict(scope.get("headers") or []).get(b"content-length", b"")
        if declared.isdigit() and int(declared) > self.max_bytes:
            await self._reject(scope, receive, send, reason="content_length")
            return

        received = 0
        exceeded = False
        response_started = False

        async def limited_receive() -> Message:
            nonlocal received, exceeded
            message = await receive()
            if message["type"] == "http.request":
                received += len(message.get("body", b""))
                if received > self.max_bytes:
                    exceeded = True
                    raise _BodyTooLargeError
            return message

        async def guarded_send(message: Message) -> None:
            nonlocal response_started
            # FastAPI turns an error while reading the form into its own 400; the 413 replaces it.
            if exceeded:
                return
            if message["type"] == "http.response.start":
                response_started = True
            await send(message)

        with contextlib.suppress(_BodyTooLargeError):
            await self.app(scope, limited_receive, guarded_send)
        if exceeded and not response_started:
            await self._reject(scope, receive, send, reason="body_bytes")

    async def _reject(self, scope: Scope, receive: Receive, send: Send, *, reason: str) -> None:
        DOMAIN_ERRORS.labels(application=APPLICATION, code="FILE_TOO_LARGE").inc()
        logger.warning("request.rejected", code="FILE_TOO_LARGE", http_status=413, reason=reason)
        await problem_response(
            413,
            "FILE_TOO_LARGE",
            "The request body exceeds the size limit",
            _accept_language(scope),
            maxMegabytes=self.max_file_megabytes,
        )(scope, receive, send)
