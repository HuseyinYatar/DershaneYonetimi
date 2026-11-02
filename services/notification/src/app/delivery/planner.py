"""Who gets which notification on which channel (step A of the worker).

Three simple queries, each on one table, then the rows are put together in Python:
  1. guardian_link_replica   student -> guardian
  2. contact_replica         person  -> name, e-mail, phone, active
  3. notification_preference channels the person turned off for this event type
The recipient list itself comes from the event (studentIds); this service keeps no exam data.
"""

import uuid
from collections import Counter
from dataclasses import dataclass
from datetime import date

import structlog
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.delivery.templates import Audience, render, turkish_date
from app.models import ContactReplica, GuardianLinkReplica, Notification, NotificationPreference
from app.models.base import utcnow
from app.models.enums import Channel, DeliveryStatus
from app.observability.metrics import NOTIFICATIONS_CREATED, NOTIFICATIONS_SKIPPED

logger = structlog.get_logger(__name__)

# A late result is still an exam result: one preference ("exam results") covers both events.
PREFERENCE_EVENT_TYPES = {"StudentResultAdded": "ExamResultsPublished"}


@dataclass(frozen=True)
class ExamInfo:
    mock_exam_id: uuid.UUID
    exam_type_code: str | None
    name: str | None
    exam_date: date | None

    @property
    def display_name(self) -> str:
        if self.name:
            return self.name
        return f"{self.exam_type_code} denemesi" if self.exam_type_code else "Deneme"


def choose_channels(
    person: ContactReplica, disabled: set[tuple[uuid.UUID, Channel]]
) -> tuple[list[Channel], list[tuple[Channel, str]]]:
    """Channels to write for an active person, and the skipped ones with their reason."""
    channels = [Channel.IN_APP]  # cannot be turned off
    skipped: list[tuple[Channel, str]] = []
    for channel, address in ((Channel.EMAIL, person.email), (Channel.SMS, person.phone)):
        if not address:
            skipped.append((channel, "no_address"))
        elif (person.user_id, channel) in disabled:
            skipped.append((channel, "preference_off"))
        else:
            channels.append(channel)
    return channels, skipped


def create_exam_result_notifications(
    session: Session,
    *,
    event_id: uuid.UUID,
    event_type: str,
    student_ids: list[uuid.UUID],
    exam: ExamInfo,
    frontend_base_url: str,
) -> int:
    """Adds the notification rows to the session (the consumer commits). Returns how many were added."""
    student_ids = list(dict.fromkeys(student_ids))  # drop duplicates, keep order

    # 1. Guardians of the students
    guardians = dict(
        session.execute(
            select(GuardianLinkReplica.student_id, GuardianLinkReplica.guardian_id).where(
                GuardianLinkReplica.student_id.in_(student_ids)
            )
        ).all()
    )
    everyone = [*student_ids, *guardians.values()]

    # 2. Names and contact details of the students and the guardians
    people = {
        person.user_id: person
        for person in session.scalars(select(ContactReplica).where(ContactReplica.user_id.in_(everyone)))
    }

    # 3. Channels they turned off for this kind of notification
    preference_event_type = PREFERENCE_EVENT_TYPES.get(event_type, event_type)
    disabled = {
        (user_id, channel)
        for user_id, channel in session.execute(
            select(NotificationPreference.user_id, NotificationPreference.channel).where(
                NotificationPreference.user_id.in_(everyone),
                NotificationPreference.event_type == preference_event_type,
            )
        ).all()
    }

    now = utcnow()
    skipped: Counter[str] = Counter()
    rows: list[Notification] = []
    for student_id in student_ids:
        student = people.get(student_id)
        audiences: list[tuple[uuid.UUID, Audience]] = [(student_id, "student")]
        if student_id in guardians:
            audiences.append((guardians[student_id], "guardian"))

        for recipient_id, audience in audiences:
            person = people.get(recipient_id)
            if person is None:
                # Not in contact_replica yet: UserUpdated not received (or the user existed before the
                # subscription and was never backfilled). The rest of the event goes on.
                skipped["recipient_missing"] += 1
                logger.warning(
                    "notification.recipient_missing", recipient_id=str(recipient_id), audience=audience
                )
                continue
            if not person.active:
                skipped["inactive"] += 1
                logger.debug(
                    "notification.recipient_skipped", recipient_id=str(recipient_id), reason="inactive"
                )
                continue

            channels, skipped_channels = choose_channels(person, disabled)
            for channel, reason in skipped_channels:
                skipped[reason] += 1
                logger.debug(
                    "notification.channel_skipped",
                    recipient_id=str(recipient_id),
                    channel=channel.value,
                    reason=reason,
                )

            values = {
                "recipient_first_name": person.first_name,
                "student_full_name": f"{student.first_name} {student.last_name}" if student else "Öğrenciniz",
                "exam_name": exam.display_name,
                "exam_date": turkish_date(exam.exam_date),
                "link": f"{frontend_base_url}/students/{student_id}/results/{exam.mock_exam_id}",
            }
            for channel in channels:
                text = render(event_type, audience, channel, values)
                in_app = channel is Channel.IN_APP
                rows.append(
                    Notification(
                        recipient_id=recipient_id,
                        channel=channel,
                        event_type=event_type,
                        title=text.title,
                        body=text.body,
                        status=DeliveryStatus.SENT if in_app else DeliveryStatus.PENDING,
                        sent_at=now if in_app else None,
                        source_event_id=event_id,
                    )
                )

    session.add_all(rows)
    for row in rows:
        NOTIFICATIONS_CREATED.labels(channel=row.channel.value).inc()
    for reason, count in skipped.items():
        NOTIFICATIONS_SKIPPED.labels(reason=reason).inc(count)
    # One summary line per event: every skip reason is counted here (details are DEBUG lines above).
    logger.info(
        "notification.planned",
        students=len(student_ids),
        guardians=len(guardians),
        created=len(rows),
        skipped=dict(skipped),
    )
    return len(rows)
