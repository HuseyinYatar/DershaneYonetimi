"""Delivery of pending e-mail and SMS notifications (step B of the worker).

Rows are claimed one at a time with FOR UPDATE SKIP LOCKED, so several worker replicas never send
the same notification, and each result is committed before the next row is claimed.
"""

from datetime import timedelta

import structlog
from sqlalchemy import func, or_, select
from sqlalchemy.orm import Session, sessionmaker

from app.config import Settings
from app.delivery.senders import EmailSender, PermanentDeliveryError, SmsSender, TemporaryDeliveryError
from app.models import ContactReplica, Notification
from app.models.base import utcnow
from app.models.enums import Channel, DeliveryStatus
from app.observability.metrics import DELIVERIES, NOTIFICATIONS_PENDING

logger = structlog.get_logger(__name__)


class Dispatcher:
    def __init__(
        self,
        session_factory: sessionmaker[Session],
        email_sender: EmailSender,
        sms_sender: SmsSender,
        settings: Settings,
    ) -> None:
        self._session_factory = session_factory
        self._email = email_sender
        self._sms = sms_sender
        self._batch_size = settings.delivery_batch_size
        self._max_attempts = settings.delivery_max_attempts
        self._backoff = settings.delivery_backoff_seconds

    def deliver_batch(self) -> int:
        """Delivers up to batch_size due notifications. Returns how many were attempted."""
        attempted = 0
        while attempted < self._batch_size:
            with self._session_factory() as session:
                notification = self._claim_next(session)
                if notification is None:
                    break
                self._deliver(session, notification)
                session.commit()
            attempted += 1

        with self._session_factory() as session:
            NOTIFICATIONS_PENDING.set(
                session.scalar(select(func.count()).where(Notification.status == DeliveryStatus.PENDING)) or 0
            )
        if attempted == 0:
            logger.debug("delivery.nothing_due")
        return attempted

    @staticmethod
    def _claim_next(session: Session) -> Notification | None:
        return session.scalars(
            select(Notification)
            .where(
                Notification.status == DeliveryStatus.PENDING,
                or_(Notification.next_attempt_at.is_(None), Notification.next_attempt_at <= utcnow()),
            )
            .order_by(Notification.created_at)
            .limit(1)
            .with_for_update(skip_locked=True)
        ).first()

    def _deliver(self, session: Session, notification: Notification) -> None:
        log = logger.bind(notification_id=str(notification.id), channel=notification.channel.value)

        # The address is read now, not when the row was written: a changed e-mail is used at once.
        contact = session.get(ContactReplica, notification.recipient_id)
        address = None
        if contact is not None and contact.active:
            address = contact.email if notification.channel is Channel.EMAIL else contact.phone
        if address is None:
            reason = (
                "recipient_missing" if contact is None else "inactive" if not contact.active else "no_address"
            )
            notification.status = DeliveryStatus.FAILED
            notification.last_error = f"undeliverable: {reason}"
            DELIVERIES.labels(channel=notification.channel.value, outcome="failed").inc()
            log.warning("notification.undeliverable", reason=reason)
            return

        notification.attempt_count += 1
        try:
            if notification.channel is Channel.EMAIL:
                self._email.send(address, notification.title, notification.body)
            else:
                self._sms.send(address, notification.body)
        except PermanentDeliveryError as exc:
            self._fail(notification, str(exc))
            log.warning("notification.delivery_failed", error=str(exc), attempts=notification.attempt_count)
        except TemporaryDeliveryError as exc:
            if notification.attempt_count >= self._max_attempts:
                self._fail(notification, str(exc))
                log.error("notification.delivery_failed", error=str(exc), attempts=notification.attempt_count)
                return
            delay = self._backoff[min(notification.attempt_count, len(self._backoff)) - 1]
            notification.next_attempt_at = utcnow() + timedelta(seconds=delay)
            notification.last_error = str(exc)
            DELIVERIES.labels(channel=notification.channel.value, outcome="retry").inc()
            log.warning(
                "notification.delivery_retry_scheduled",
                error=str(exc),
                attempts=notification.attempt_count,
                retry_in_seconds=delay,
            )
        else:
            notification.status = DeliveryStatus.SENT
            notification.sent_at = utcnow()
            notification.next_attempt_at = None
            DELIVERIES.labels(channel=notification.channel.value, outcome="sent").inc()
            log.info("notification.sent", attempts=notification.attempt_count)

    @staticmethod
    def _fail(notification: Notification, error: str) -> None:
        notification.status = DeliveryStatus.FAILED
        notification.last_error = error
        notification.next_attempt_at = None
        DELIVERIES.labels(channel=notification.channel.value, outcome="failed").inc()
