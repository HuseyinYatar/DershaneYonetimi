"""OpenTelemetry setup. The OTLP exporter reads the standard OTEL_EXPORTER_OTLP_* variables."""

import structlog
from fastapi import FastAPI
from opentelemetry import context, trace
from opentelemetry.propagate import extract
from sqlalchemy.engine import Engine

from app.config import Settings

logger = structlog.get_logger(__name__)


def configure_tracing(
    settings: Settings, *, app: FastAPI | None = None, engine: Engine | None = None
) -> None:
    if not settings.otel_enabled:
        logger.info("tracing.disabled", reason="otel_enabled_false")
        return

    from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter
    from opentelemetry.instrumentation.botocore import BotocoreInstrumentor
    from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor
    from opentelemetry.instrumentation.sqlalchemy import SQLAlchemyInstrumentor
    from opentelemetry.sdk.resources import Resource
    from opentelemetry.sdk.trace import TracerProvider
    from opentelemetry.sdk.trace.export import BatchSpanProcessor

    provider = TracerProvider(
        resource=Resource.create(
            {"service.name": settings.service_name, "deployment.environment": settings.environment}
        )
    )
    provider.add_span_processor(BatchSpanProcessor(OTLPSpanExporter()))
    trace.set_tracer_provider(provider)

    if app is not None:
        FastAPIInstrumentor.instrument_app(app, excluded_urls="health/live,health/ready,metrics")
    if engine is not None:
        SQLAlchemyInstrumentor().instrument(engine=engine)
    BotocoreInstrumentor().instrument()
    logger.info("tracing.enabled")


def producer_context(traceparent: str | None) -> context.Context:
    """Trace context of the request that published the event (SNS message attribute `traceparent`).

    Spans started with it continue the producer's trace, so one request can be followed from the
    user or exam-analysis service to the notification in Grafana.
    """
    return extract({"traceparent": traceparent} if traceparent else {})
