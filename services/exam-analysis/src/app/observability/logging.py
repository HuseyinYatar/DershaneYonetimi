"""Structured JSON logging for Loki.

All records (ours, uvicorn's, SQLAlchemy's) go through the same structlog pipeline and are
written to stdout as one JSON object per line. Context such as request_id, job_id and
mock_exam_id is bound with structlog.contextvars; OpenTelemetry trace and span ids are added
when a span is active.

KVKK: log events carry identifiers (UUIDs), counts, durations and states only. Student names,
student numbers, raw optical lines and given answers must never be passed to a logger.
"""

import logging
import sys
from typing import Any

import structlog
from opentelemetry import trace

_HANDLER_NAME = "exam-analysis-json"


def _add_trace_context(_: Any, __: str, event_dict: dict[str, Any]) -> dict[str, Any]:
    span_context = trace.get_current_span().get_span_context()
    if span_context.is_valid:
        event_dict["trace_id"] = format(span_context.trace_id, "032x")
        event_dict["span_id"] = format(span_context.span_id, "016x")
    return event_dict


def _service_adder(service_name: str, environment: str) -> structlog.types.Processor:
    def add_service(_: Any, __: str, event_dict: dict[str, Any]) -> dict[str, Any]:
        event_dict.setdefault("service", service_name)
        event_dict.setdefault("environment", environment)
        return event_dict

    return add_service


def configure_logging(level: str, service_name: str, environment: str) -> None:
    shared_processors: list[structlog.types.Processor] = [
        structlog.contextvars.merge_contextvars,
        structlog.stdlib.add_log_level,
        structlog.stdlib.add_logger_name,
        structlog.processors.TimeStamper(fmt="iso", utc=True),
        _service_adder(service_name, environment),
        _add_trace_context,
    ]
    structlog.configure(
        processors=[*shared_processors, structlog.stdlib.ProcessorFormatter.wrap_for_formatter],
        logger_factory=structlog.stdlib.LoggerFactory(),
        wrapper_class=structlog.stdlib.BoundLogger,
        cache_logger_on_first_use=True,
    )
    formatter = structlog.stdlib.ProcessorFormatter(
        foreign_pre_chain=shared_processors,
        processors=[
            structlog.stdlib.ProcessorFormatter.remove_processors_meta,
            structlog.processors.format_exc_info,
            structlog.processors.JSONRenderer(ensure_ascii=False),
        ],
    )
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(formatter)
    handler.set_name(_HANDLER_NAME)

    root = logging.getLogger()
    # Replace only our own handler so reconfiguration keeps handlers added by others (e.g. pytest).
    root.handlers = [existing for existing in root.handlers if existing.get_name() != _HANDLER_NAME]
    root.addHandler(handler)
    root.setLevel(level.upper())

    for name in ("uvicorn", "uvicorn.error"):
        logging.getLogger(name).handlers = []
        logging.getLogger(name).propagate = True
    # Requests are logged by RequestContextMiddleware with the route template instead of the
    # raw path, so uvicorn's access log is redundant.
    logging.getLogger("uvicorn.access").disabled = True
    logging.getLogger("botocore").setLevel(logging.WARNING)
    logging.getLogger("urllib3").setLevel(logging.WARNING)
