"""What each consumed event does. Handlers only add changes to the session; the consumer commits them
together with the processed_event row, so an event is applied exactly once or not at all."""

import uuid
from collections.abc import Callable
from datetime import datetime
from typing import Any
from zoneinfo import ZoneInfo

import structlog
from sqlalchemy import delete
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.orm import Session

from app.delivery.planner import ExamInfo, ValuesFor, plan_notifications
from app.delivery.templates import clock_time, turkish_date, turkish_datetime
from app.events.payloads import (
    AssignmentEvent,
    AttendanceRecordEvent,
    EventEnvelope,
    EventModel,
    ExamResultsPublished,
    GuardianLinked,
    GuardianUnlinked,
    StudentAbsent,
    StudentExcusedByTeacher,
    StudentResultAdded,
    SubmissionGraded,
    TaskAssigned,
    TaskDueSoon,
    TaskOverdue,
    UserUpdated,
)
from app.models import ContactReplica, GuardianLinkReplica
from app.models.base import utcnow
from app.observability.metrics import NOTIFICATIONS_SKIPPED, REPLICA_EVENTS_IGNORED

logger = structlog.get_logger(__name__)


DEFAULT_TIME_ZONE = ZoneInfo("Europe/Istanbul")


class EventHandlers:
    def __init__(
        self,
        frontend_base_url: str,
        time_zone: ZoneInfo = DEFAULT_TIME_ZONE,
        clock: Callable[[], datetime] = utcnow,
    ) -> None:
        self._frontend_base_url = frontend_base_url
        # Task deadlines arrive in UTC; the texts show them in the institution's time zone.
        self._time_zone = time_zone
        self._clock = clock
        self._routes: dict[str, tuple[type[EventModel], Callable[[Session, EventEnvelope, Any], None]]] = {
            "UserUpdated": (UserUpdated, self._user_updated),
            "GuardianLinked": (GuardianLinked, self._guardian_linked),
            "GuardianUnlinked": (GuardianUnlinked, self._guardian_unlinked),
            "ExamResultsPublished": (ExamResultsPublished, self._exam_results_published),
            "StudentResultAdded": (StudentResultAdded, self._student_result_added),
            "StudentAbsent": (StudentAbsent, self._attendance_record),
            "StudentExcusedByTeacher": (StudentExcusedByTeacher, self._attendance_record),
            "TaskAssigned": (TaskAssigned, self._assignment),
            "TaskDueSoon": (TaskDueSoon, self._task_due_soon),
            "TaskOverdue": (TaskOverdue, self._assignment),
            "SubmissionGraded": (SubmissionGraded, self._assignment),
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
        exam = ExamInfo(event.mock_exam_id, event.exam_type_code, event.mock_exam_name, event.exam_date)
        plan_notifications(
            session,
            event_id=envelope.event_id,
            event_type=envelope.event_type,
            student_ids=event.student_ids,
            values_for=self._exam_values(exam),
        )

    def _student_result_added(
        self, session: Session, envelope: EventEnvelope, event: StudentResultAdded
    ) -> None:
        exam = ExamInfo(event.mock_exam_id, None, event.mock_exam_name, event.exam_date)
        plan_notifications(
            session,
            event_id=envelope.event_id,
            event_type=envelope.event_type,
            student_ids=[event.student_id],
            values_for=self._exam_values(exam),
        )

    def _exam_values(self, exam: ExamInfo) -> ValuesFor:
        def values(student_id: uuid.UUID) -> dict[str, Any]:
            return {
                "exam_name": exam.display_name,
                "exam_date": turkish_date(exam.exam_date),
                "link": f"{self._frontend_base_url}/students/{student_id}/results/{exam.mock_exam_id}",
            }

        return values

    # --- attendance: notifications --------------------------------------------------------------

    def _attendance_record(
        self, session: Session, envelope: EventEnvelope, event: AttendanceRecordEvent
    ) -> None:
        """StudentAbsent and StudentExcusedByTeacher: same values, different texts (app.delivery.kinds)."""
        values = {
            "session_date": turkish_date(event.session_date),
            "start_time": clock_time(event.start_time),
            "end_time": clock_time(event.end_time),
            "link": f"{self._frontend_base_url}/students/{event.student_id}/attendance",
        }
        plan_notifications(
            session,
            event_id=envelope.event_id,
            event_type=envelope.event_type,
            student_ids=[event.student_id],
            values_for=lambda _: values,
        )

    # --- task: notifications --------------------------------------------------------------------

    def _assignment(self, session: Session, envelope: EventEnvelope, event: AssignmentEvent) -> None:
        """TaskAssigned, TaskOverdue and SubmissionGraded: one assignment of one student."""
        values = {
            "task_title": event.title,
            "due_at": turkish_datetime(event.due_at, self._time_zone),
            "link": f"{self._frontend_base_url}/students/{event.student_id}/tasks/{event.assignment_id}",
        }
        plan_notifications(
            session,
            event_id=envelope.event_id,
            event_type=envelope.event_type,
            student_ids=[event.student_id],
            values_for=lambda _: values,
        )

    def _task_due_soon(self, session: Session, envelope: EventEnvelope, event: TaskDueSoon) -> None:
        # A reminder read after the deadline (the queue was backed up, the worker was down) would
        # tell the student to hurry for a deadline that is already gone; TaskOverdue covers it.
        if event.due_at <= self._clock():
            NOTIFICATIONS_SKIPPED.labels(reason="past_due").inc()
            logger.info(
                "notification.event_skipped",
                event_type=envelope.event_type,
                assignment_id=str(event.assignment_id),
                reason="past_due",
            )
            return
        self._assignment(session, envelope, event)
