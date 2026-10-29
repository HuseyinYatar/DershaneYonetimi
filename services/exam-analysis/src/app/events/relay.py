import structlog
from sqlalchemy import func, select
from sqlalchemy.orm import Session, sessionmaker

from app.events.publisher import EventPublisher
from app.models import OutboxEvent
from app.models.base import utcnow
from app.observability.metrics import OUTBOX_PENDING, OUTBOX_PUBLISH_FAILURES, OUTBOX_PUBLISHED

logger = structlog.get_logger(__name__)

MAX_ERROR_LENGTH = 1000

class OutboxRelay:
    def __init__(
        self, session_factory: sessionmaker[Session], publisher: EventPublisher, batch_size: int
    ) -> None:
        self._session_factory = session_factory
        self._publisher = publisher
        self._batch_size = batch_size

    def relay_batch(self) -> int:
        """Publishes one batch of unpublished events; returns how many were published.
        Rows are locked with SKIP LOCKED so several worker replicas never send the same event
        twice in parallel. Delivery is still at-least-once: consumers deduplicate on eventId.
        """
        published = 0
        with self._session_factory() as session:
            # DB'den evetler geliyor
            events = session.scalars(
                select(OutboxEvent)
                .where(OutboxEvent.published_at.is_(None))
                .order_by(OutboxEvent.created_at)
                .limit(self._batch_size)
                .with_for_update(skip_locked=True)
            ).all()
            for event in events:
                try:
                    self._publisher.publish(event.envelope, event.traceparent)
                except Exception as exc:  # broker errors vary by adapter; every failure is recorded
                    event.attempts += 1
                    event.last_error = f"{type(exc).__name__}: {exc}"[:MAX_ERROR_LENGTH]
                    OUTBOX_PUBLISH_FAILURES.labels(event_type=event.event_type).inc()
                    logger.exception(
                        "outbox.publish_failed",
                        event_id=str(event.id),
                        event_type=event.event_type,
                        attempts=event.attempts,
                    )
                    continue
                event.published_at = utcnow()
                event.attempts += 1
                published += 1
                OUTBOX_PUBLISHED.labels(event_type=event.event_type).inc()
                logger.info("outbox.event_published", event_id=str(event.id), event_type=event.event_type)
            session.commit()
            pending = session.scalar(
                select(func.count()).select_from(OutboxEvent).where(OutboxEvent.published_at.is_(None))
            )
        OUTBOX_PENDING.set(pending or 0)
        return published
