"""What each consumed event does to student_replica. Handlers only add changes to the session; the
consumer commits them together with the processed_event row, so an event is applied once or not at all.

SNS keeps no order, so an event may arrive before or after a newer one:
  - UserUpdated owns the number, name, branch and active flag; an older recordVersion changes nothing.
  - StudentAddedToClassroom creates the row if UserUpdated has not arrived yet, and otherwise changes
    only class_id. A later UserUpdated always wins over the name and number in this event.
  - StudentRemovedFromClassroom clears class_id only if it is still that classroom, so a late removal
    from the old classroom cannot clear the new one.
"""

from collections.abc import Callable
from typing import Any

import structlog
from sqlalchemy import or_, update
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.orm import Session

from app.events.payloads import (
    CONSUMED_EVENTS,
    EventEnvelope,
    StudentAddedToClassroom,
    StudentRemovedFromClassroom,
    UserUpdated,
)
from app.models import StudentReplica
from app.models.base import utcnow
from app.observability.metrics import REPLICA_EVENTS_IGNORED

logger = structlog.get_logger(__name__)

STUDENT_ROLE = "STUDENT"


class EventHandlers:
    def __init__(self) -> None:
        self._routes: dict[str, Callable[[Session, Any], None]] = {
            "UserUpdated": self._user_updated,
            "StudentAddedToClassroom": self._student_added,
            "StudentRemovedFromClassroom": self._student_removed,
        }

    def handles(self, event_type: str) -> bool:
        return event_type in self._routes

    def handle(self, session: Session, envelope: EventEnvelope) -> None:
        """Raises pydantic.ValidationError when `data` does not match the event's contract."""
        model = CONSUMED_EVENTS[envelope.event_type]
        self._routes[envelope.event_type](session, model.model_validate(envelope.data))

    @staticmethod
    def _user_updated(session: Session, event: UserUpdated) -> None:
        if event.role != STUDENT_ROLE:
            REPLICA_EVENTS_IGNORED.labels(event_type="UserUpdated", reason="not_a_student").inc()
            logger.debug("student_replica.update_ignored", user_id=str(event.user_id), reason="not_a_student")
            return
        if event.student_number is None:
            REPLICA_EVENTS_IGNORED.labels(event_type="UserUpdated", reason="student_number_missing").inc()
            logger.warning(
                "student_replica.update_ignored", user_id=str(event.user_id), reason="student_number_missing"
            )
            return

        values = {
            "student_no": event.student_number,
            "branch_id": event.branch_id,
            "full_name": f"{event.first_name} {event.last_name}",
            "is_active": event.active,
            "record_version": event.record_version,
            "updated_at": utcnow(),
        }
        statement = insert(StudentReplica).values(student_id=event.user_id, **values)
        statement = statement.on_conflict_do_update(
            index_elements=[StudentReplica.student_id],
            set_=values,
            where=or_(
                StudentReplica.record_version.is_(None),
                StudentReplica.record_version < statement.excluded.record_version,
            ),
        ).returning(StudentReplica.student_id)
        # RETURNING yields no row when the WHERE above kept the stored (newer) version.
        if session.execute(statement).first() is None:
            REPLICA_EVENTS_IGNORED.labels(event_type="UserUpdated", reason="older_version").inc()
            logger.info(
                "student_replica.update_ignored",
                student_id=str(event.user_id),
                record_version=event.record_version,
                reason="older_version",
            )
            return
        logger.info(
            "student_replica.upserted",
            student_id=str(event.user_id),
            record_version=event.record_version,
            active=event.active,
        )

    @staticmethod
    def _student_added(session: Session, event: StudentAddedToClassroom) -> None:
        now = utcnow()
        session.execute(
            insert(StudentReplica)
            .values(
                student_id=event.student_id,
                student_no=event.student_number,
                branch_id=event.branch_id,
                class_id=event.classroom_id,
                full_name=event.full_name,
                is_active=True,
                updated_at=now,
            )
            .on_conflict_do_update(
                index_elements=[StudentReplica.student_id],
                set_={"class_id": event.classroom_id, "updated_at": now},
            )
        )
        logger.info(
            "student_replica.classroom_set",
            student_id=str(event.student_id),
            classroom_id=str(event.classroom_id),
        )

    @staticmethod
    def _student_removed(session: Session, event: StudentRemovedFromClassroom) -> None:
        cleared = session.execute(
            update(StudentReplica)
            .where(
                StudentReplica.student_id == event.student_id,
                StudentReplica.class_id == event.classroom_id,
            )
            .values(class_id=None, updated_at=utcnow())
            .returning(StudentReplica.student_id)
        ).first()
        if cleared is None:
            REPLICA_EVENTS_IGNORED.labels(
                event_type="StudentRemovedFromClassroom", reason="classroom_mismatch"
            ).inc()
            logger.info(
                "student_replica.removal_ignored",
                student_id=str(event.student_id),
                classroom_id=str(event.classroom_id),
                reason="classroom_mismatch",
            )
            return
        logger.info(
            "student_replica.classroom_cleared",
            student_id=str(event.student_id),
            classroom_id=str(event.classroom_id),
        )
