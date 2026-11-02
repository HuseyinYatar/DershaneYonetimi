"""Queue consumer, one step of the worker loop (`python -m app.worker`).

For every message, in one database transaction:
  1. validate the envelope           invalid  -> not deleted: SQS retries, then moves it to the DLQ
  2. INSERT processed_event          conflict -> duplicate delivery: skipped and deleted
  3. run the event's handler         error    -> rolled back, not deleted: retried, then DLQ
  4. COMMIT, then delete the message
A message is deleted only after its changes are committed. If the delete itself fails, the message
comes back and step 2 skips it, so nothing is applied twice.
"""

import structlog
from opentelemetry import trace
from pydantic import ValidationError
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.orm import Session, sessionmaker

from app.events.handlers import EventHandlers
from app.events.payloads import EventEnvelope
from app.events.queue import MessageQueue, QueueMessage
from app.models import ProcessedEvent
from app.observability.metrics import EVENTS_CONSUMED
from app.observability.tracing import producer_context

logger = structlog.get_logger(__name__)
tracer = trace.get_tracer(__name__)

SUPPORTED_SCHEMA_VERSION = 1


def validation_errors(exc: ValidationError) -> list[dict[str, object]]:
    # Field locations and error types only: the input values may be personal data.
    return [{"loc": list(error["loc"]), "type": error["type"]} for error in exc.errors()]


class EventConsumer:
    def __init__(
        self, session_factory: sessionmaker[Session], queue: MessageQueue, handlers: EventHandlers
    ) -> None:
        self._session_factory = session_factory
        self._queue = queue
        self._handlers = handlers

    def consume_batch(self) -> int:
        """Receives one batch (long polling) and processes it. Returns the number of messages received."""
        messages = self._queue.receive()
        for message in messages:
            structlog.contextvars.bind_contextvars(message_id=message.message_id)
            try:
                self._process(message)
            finally:
                structlog.contextvars.unbind_contextvars("message_id", "event_id", "event_type")
        return len(messages)

    def _process(self, message: QueueMessage) -> None:
        try:
            envelope = EventEnvelope.model_validate_json(message.body)
        except ValidationError as exc:
            EVENTS_CONSUMED.labels(event_type=message.event_type or "unknown", outcome="invalid").inc()
            logger.error(
                "event.invalid_envelope",
                receive_count=message.receive_count,
                errors=validation_errors(exc),
                effect="left in the queue; moves to the DLQ after maxReceiveCount",
            )
            return

        event_type = envelope.event_type
        structlog.contextvars.bind_contextvars(event_id=str(envelope.event_id), event_type=event_type)

        if envelope.schema_version != SUPPORTED_SCHEMA_VERSION:
            EVENTS_CONSUMED.labels(event_type=event_type, outcome="invalid").inc()
            logger.error(
                "event.unsupported_schema_version",
                schema_version=envelope.schema_version,
                supported=SUPPORTED_SCHEMA_VERSION,
                effect="left in the queue; moves to the DLQ after maxReceiveCount",
            )
            return

        if not self._handlers.handles(event_type):
            # The subscription filter policy should keep these out of the queue.
            EVENTS_CONSUMED.labels(event_type=event_type, outcome="unknown_type").inc()
            logger.warning("event.unknown_type_skipped", reason="no_handler")
            self._delete(message)
            return

        with (
            tracer.start_as_current_span(
                f"consume {event_type}",
                context=producer_context(message.traceparent),
                kind=trace.SpanKind.CONSUMER,
            ),
            self._session_factory() as session,
        ):
            try:
                first_delivery = session.execute(
                    insert(ProcessedEvent)
                    .values(event_id=envelope.event_id, event_type=event_type)
                    .on_conflict_do_nothing()
                    .returning(ProcessedEvent.event_id)
                ).first()
                if first_delivery is None:
                    session.rollback()
                    EVENTS_CONSUMED.labels(event_type=event_type, outcome="duplicate").inc()
                    logger.info("event.duplicate_skipped", reason="event_id already processed")
                    self._delete(message)
                    return

                self._handlers.handle(session, envelope)
                session.commit()
            except ValidationError as exc:
                session.rollback()
                EVENTS_CONSUMED.labels(event_type=event_type, outcome="invalid").inc()
                logger.error(
                    "event.invalid_data",
                    receive_count=message.receive_count,
                    errors=validation_errors(exc),
                    effect="left in the queue; moves to the DLQ after maxReceiveCount",
                )
                return
            except Exception:
                # e.g. the database is unreachable: the message becomes visible again and is retried.
                session.rollback()
                EVENTS_CONSUMED.labels(event_type=event_type, outcome="failed").inc()
                logger.exception("event.failed", receive_count=message.receive_count)
                return

        EVENTS_CONSUMED.labels(event_type=event_type, outcome="processed").inc()
        logger.info("event.processed")
        self._delete(message)

    def _delete(self, message: QueueMessage) -> None:
        try:
            self._queue.delete(message)
        except Exception as exc:
            # Changes are committed; the redelivered message will be skipped as a duplicate.
            logger.warning("event.delete_failed", error=type(exc).__name__, effect="redelivered and skipped")
