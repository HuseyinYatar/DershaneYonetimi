"""Every notification kind in one table: which texts it uses, who gets it, on which channels, and
under which on/off preference it falls.

Adding an event that sends notifications = one row here, a template folder, a payload model and a
handler. The planner reads the rest from this table.
"""

from dataclasses import dataclass

from app.models.enums import Channel

ALL_CHANNELS = frozenset(Channel)
IN_APP_AND_EMAIL = frozenset({Channel.IN_APP, Channel.EMAIL})


@dataclass(frozen=True)
class NotificationKind:
    # Folder under app/templates/ with <audience>.<title|body|sms>.txt files.
    template_set: str
    # Event type the user's on/off choice is stored under (notification_preference.event_type).
    # Related events share one, so a single switch turns off e.g. every task notification.
    preference: str
    # Channels this kind may use. IN_APP is always among them and cannot be turned off.
    channels: frozenset[Channel]
    # False: only the student. True: the student and their guardian.
    include_guardians: bool


KINDS: dict[str, NotificationKind] = {
    # exam-analysis
    "ExamResultsPublished": NotificationKind("exam_result", "ExamResultsPublished", ALL_CHANNELS, True),
    # A late result is still an exam result: the same texts and the same preference.
    "StudentResultAdded": NotificationKind("exam_result", "ExamResultsPublished", ALL_CHANNELS, True),
    # attendance
    "StudentAbsent": NotificationKind("attendance_absent", "StudentAbsent", ALL_CHANNELS, True),
    "StudentExcusedByTeacher": NotificationKind("attendance_excused", "StudentAbsent", ALL_CHANNELS, True),
    # task: no SMS. A daily task creates an assignment every day, an SMS each time would be too many.
    "TaskAssigned": NotificationKind("task_assigned", "TaskAssigned", IN_APP_AND_EMAIL, False),
    "TaskDueSoon": NotificationKind("task_due_soon", "TaskAssigned", IN_APP_AND_EMAIL, False),
    "TaskOverdue": NotificationKind("task_overdue", "TaskAssigned", IN_APP_AND_EMAIL, True),
    "SubmissionGraded": NotificationKind("submission_graded", "TaskAssigned", IN_APP_AND_EMAIL, False),
}

# Channels a user may turn off for each preference: the union of what its kinds use, minus IN_APP.
CONFIGURABLE_CHANNELS_BY_PREFERENCE: dict[str, tuple[Channel, ...]] = {
    preference: tuple(
        channel
        for channel in (Channel.EMAIL, Channel.SMS)
        if any(channel in kind.channels for kind in KINDS.values() if kind.preference == preference)
    )
    for preference in dict.fromkeys(kind.preference for kind in KINDS.values())
}
