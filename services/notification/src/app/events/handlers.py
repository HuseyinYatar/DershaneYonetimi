"""What each consumed event does. Handlers only add changes to the session; the consumer commits them
together with the processed_event row, so an event is applied exactly once or not at all."""

from collections.abc import Callable
from typing import Any

import structlog
from sqlalchemy import delete
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.orm import Session

from app.delivery.planner import ExamInfo, create_exam_result_notifications
from app.events.payloads import (
    EventEnvelope,
    EventModel,
    ExamResultsPublished,
    GuardianLinked,
    GuardianUnlinked,
    StudentResultAdded,
    UserUpdated,
)
from app.models import ContactReplica, GuardianLinkReplica
from app.models.base import utcnow
from app.observability.metrics import REPLICA_EVENTS_IGNORED

logger = structlog.get_logger(__name__)


class EventHandlers:
    def __init__(self, frontend_base_url: str) -> None:
        self._frontend_base_url = frontend_base_url
        self._routes: dict[str, tuple[type[EventModel], Callable[[Session, EventEnvelope, Any], None]]] = {
            "UserUpdated": (UserUpdated, self._user_updated),
            "GuardianLinked": (GuardianLinked, self._guardian_linked),
            "GuardianUnlinked": (GuardianUnlinked, self._guardian_unlinked),
            "ExamResultsPublished": (ExamResultsPublished, self._exam_results_published),
            "StudentResultAdded": (StudentResultAdded, self._student_result_added),
        }

    def handles(self, event_type: str) -> bool:
        return event_type in self._routes

    def handle(self, session: Session, envelope: EventEnvelope) -> None:
        """Raises pydantic.ValidationError when `data` does not match the event's contract."""
        model, handler = self._routes[envelope.event_type]
        handler(session, envelope, model.model_validate(envelope.data))

    # --- user service: local copies -------------------------------------------------------------

    @staticmethod
    def _user_updated(session: Session, envelope: EventEnvelope, event: UserUpdated) -> None:
        values = {
            "role": event.role,
            "branch_id": event.branch_id,
            "first_name": event.first_name,
            "last_name": event.last_name,
            "email": event.email,
            "phone": event.phone,
            "active": event.active,
            "record_version": event.record_version,
            "updated_at": utcnow(),
        }
        statement = insert(ContactReplica).values(user_id=event.user_id, **values)
        statement = statement.on_conflict_do_update(
            index_elements=[ContactReplica.user_id],
            set_=values,
            # SNS keeps no order: an older version arriving late must not overwrite a newer one.
            where=ContactReplica.record_version < statement.excluded.record_version,
        ).returning(ContactReplica.user_id)
        # RETURNING yields no row when the WHERE above kept the stored (newer) version.
        if session.execute(statement).first() is None:
            REPLICA_EVENTS_IGNORED.labels(event_type=envelope.event_type, reason="older_version").inc()
            logger.info(
                "contact_replica.update_ignored",
                user_id=str(event.user_id),
                record_version=event.record_version,
                reason="older_version",
            )
            return
        logger.info(
            "contact_replica.upserted", user_id=str(event.user_id), record_version=event.record_version
        )

    @staticmethod
    def _guardian_linked(session: Session, _: EventEnvelope, event: GuardianLinked) -> None:
        values = {
            "guardian_id": event.guardian_id,
            "relationship": event.relationship,
            "updated_at": utcnow(),
        }
        session.execute(
            insert(GuardianLinkReplica)
            .values(student_id=event.student_id, **values)
            .on_conflict_do_update(index_elements=[GuardianLinkReplica.student_id], set_=values)
        )
        logger.info(
            "guardian_link.upserted", student_id=str(event.student_id), guardian_id=str(event.guardian_id)
        )

    @staticmethod
    def _guardian_unlinked(session: Session, envelope: EventEnvelope, event: GuardianUnlinked) -> None:
        # The guardian must match: if the new guardian's GuardianLinked arrived first, this late
        # GuardianUnlinked of the old guardian must not remove the new link.
        removed = session.execute(
            delete(GuardianLinkReplica)
            .where(
                GuardianLinkReplica.student_id == event.student_id,
                GuardianLinkReplica.guardian_id == event.guardian_id,
            )
            .returning(GuardianLinkReplica.student_id)
        ).first()
        if removed is None:
            REPLICA_EVENTS_IGNORED.labels(event_type=envelope.event_type, reason="link_not_found").inc()
            logger.info(
                "guardian_link.unlink_ignored",
                student_id=str(event.student_id),
                guardian_id=str(event.guardian_id),
                reason="link_not_found",
            )
            return
        logger.info(
            "guardian_link.removed", student_id=str(event.student_id), guardian_id=str(event.guardian_id)
        )

    # --- exam-analysis: notifications -----------------------------------------------------------

    def _exam_results_published(
        self, session: Session, envelope: EventEnvelope, event: ExamResultsPublished
    ) -> None:
        create_exam_result_notifications(
            session,
            event_id=envelope.event_id,
            event_type=envelope.event_type,
            student_ids=event.student_ids,
            exam=ExamInfo(event.mock_exam_id, event.exam_type_code, event.mock_exam_name, event.exam_date),
            frontend_base_url=self._frontend_base_url,
        )

    def _student_result_added(
        self, session: Session, envelope: EventEnvelope, event: StudentResultAdded
    ) -> None:
        create_exam_result_notifications(
            session,
            event_id=envelope.event_id,
            event_type=envelope.event_type,
            student_ids=[event.student_id],
            exam=ExamInfo(event.mock_exam_id, None, event.mock_exam_name, event.exam_date),
            frontend_base_url=self._frontend_base_url,
        )
