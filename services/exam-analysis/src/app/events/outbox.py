import uuid

import structlog
from sqlalchemy.orm import Session

from app.events.payloads import PUBLISHED_EVENTS, EventEnvelope
from app.models import OutboxEvent
from app.models.base import utcnow
from app.observability.tracing import current_traceparent
from app.schemas.common import ApiModel

logger = structlog.get_logger(__name__)


def add_event(session: Session, payload: ApiModel) -> uuid.UUID:
    """Stages an event in the caller's transaction; the relay publishes it after commit."""
    event_type = type(payload).__name__
    if event_type not in PUBLISHED_EVENTS:
        raise ValueError(f"{event_type} is not an event this service publishes")
    envelope = EventEnvelope(
        event_id=uuid.uuid4(),
        event_type=event_type,
        occurred_at=utcnow(),
        data=payload.model_dump(mode="json", by_alias=True),
    )
    session.add(
        OutboxEvent(
            id=envelope.event_id,
            event_type=event_type,
            envelope=envelope.model_dump(mode="json", by_alias=True),
            traceparent=current_traceparent(),
        )
    )
    logger.info("outbox.event_staged", event_type=event_type, event_id=str(envelope.event_id))
    return envelope.event_id
