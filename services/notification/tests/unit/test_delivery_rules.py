import smtplib
import uuid
from datetime import UTC, date, datetime, time
from zoneinfo import ZoneInfo

import pytest

from app.config import Settings
from app.delivery.kinds import IN_APP_AND_EMAIL, KINDS
from app.delivery.planner import ExamInfo, choose_channels
from app.delivery.senders import PermanentDeliveryError, SmtpEmailSender, TemporaryDeliveryError
from app.delivery.templates import (
    TITLE_MAX_LENGTH,
    clock_time,
    render,
    shorten,
    turkish_date,
    turkish_datetime,
)
from app.models.enums import Channel, UserRole
from tests.factories import contact

COMMON = {"recipient_first_name": "Fatma", "student_full_name": "Ali Yılmaz", "link": "http://fe/x/1"}

VALUES = {**COMMON, "exam_name": "TYT Deneme 3", "exam_date": "12 Ekim 2026"}

# What the handlers pass to each template set, and a word every title must show.
SAMPLE_VALUES = {
    "exam_result": (VALUES, "TYT Deneme 3"),
    "attendance_absent": (
        {**COMMON, "session_date": "14 Kasım 2026", "start_time": "10:00", "end_time": "10:40"},
        "14 Kasım 2026",
    ),
    "attendance_excused": (
        {**COMMON, "session_date": "14 Kasım 2026", "start_time": "10:00", "end_time": "10:40"},
        "14 Kasım 2026",
    ),
    **{
        task_set: ({**COMMON, "task_title": "Paragraf testi", "due_at": "16 Kasım 2026 23:59"}, "")
        for task_set in ("task_assigned", "task_due_soon", "task_overdue", "submission_graded")
    },
}

# Every (event, audience, channel) a notification can really be written for.
RENDERABLE = [
    (event_type, audience, channel)
    for event_type, kind in sorted(KINDS.items())
    for audience in (("student", "guardian") if kind.include_guardians else ("student",))
    for channel in sorted(kind.channels)
]


class TestTemplates:
    @pytest.mark.parametrize(("event_type", "audience", "channel"), RENDERABLE)
    def test_every_template_renders(self, event_type, audience, channel) -> None:
        values, in_title = SAMPLE_VALUES[KINDS[event_type].template_set]

        text = render(event_type, audience, channel, values)

        assert in_title in text.title
        assert values["link"] in text.body
        if audience == "guardian":
            assert "Ali Yılmaz" in text.title
        if "task_title" in values:
            assert "Paragraf testi" in text.body

    def test_texts_never_contain_a_score(self) -> None:
        values, _ = SAMPLE_VALUES["submission_graded"]

        text = render("SubmissionGraded", "student", Channel.EMAIL, {**values, "score": 85})

        assert "85" not in text.title + text.body

    def test_attendance_guardian_text(self) -> None:
        values, _ = SAMPLE_VALUES["attendance_absent"]

        text = render("StudentAbsent", "guardian", Channel.SMS, values)

        assert text.body == "Ali Yılmaz 14 Kasım 2026 10:00–10:40 dersinde devamsız: http://fe/x/1"

    def test_a_long_task_title_is_cut_to_the_column_length(self) -> None:
        values, _ = SAMPLE_VALUES["task_assigned"]

        text = render("TaskAssigned", "student", Channel.IN_APP, {**values, "task_title": "a" * 200})

        assert len(text.title) == TITLE_MAX_LENGTH
        assert text.title.endswith("…")
        assert "a" * 200 in text.body  # the body keeps the whole title

    def test_guardian_text_names_the_student(self) -> None:
        text = render("ExamResultsPublished", "guardian", Channel.EMAIL, VALUES)

        assert text.title == "Ali Yılmaz: TYT Deneme 3 sonucu açıklandı"
        assert text.body.startswith("Merhaba Fatma,")
        assert "(12 Ekim 2026)" in text.body

    def test_missing_variable_is_an_error_not_an_empty_gap(self) -> None:
        from jinja2 import UndefinedError

        with pytest.raises(UndefinedError):
            render("ExamResultsPublished", "student", Channel.EMAIL, {"exam_name": "TYT"})

    def test_turkish_date_and_exam_name_fallbacks(self) -> None:
        assert turkish_date(date(2026, 2, 3)) == "3 Şubat 2026"
        assert turkish_date(None) is None
        assert ExamInfo(uuid.uuid4(), "TYT", None, None).display_name == "TYT denemesi"
        assert ExamInfo(uuid.uuid4(), None, None, None).display_name == "Deneme"

    def test_utc_deadline_is_written_in_the_institution_time_zone(self) -> None:
        istanbul = ZoneInfo("Europe/Istanbul")

        assert turkish_datetime(datetime(2026, 11, 16, 20, 59, tzinfo=UTC), istanbul) == "16 Kasım 2026 23:59"
        # Crosses midnight: 22:30 UTC is already the next day in Istanbul.
        assert turkish_datetime(datetime(2026, 12, 31, 22, 30, tzinfo=UTC), istanbul) == "1 Ocak 2027 01:30"
        assert clock_time(time(9, 5, 0)) == "09:05"

    def test_shorten(self) -> None:
        assert shorten("kısa", 10) == "kısa"
        assert shorten("abcdefghij", 5) == "abcd…"


class TestChooseChannels:
    def test_in_app_always_and_addressed_channels(self) -> None:
        person = contact(uuid.uuid4(), UserRole.GUARDIAN, "Fatma", email="f@example.com", phone="0532")

        channels, skipped = choose_channels(person, disabled=set())

        assert channels == [Channel.IN_APP, Channel.EMAIL, Channel.SMS]
        assert skipped == []

    def test_missing_address_and_turned_off_channel_are_skipped_with_a_reason(self) -> None:
        person = contact(uuid.uuid4(), UserRole.GUARDIAN, "Hasan", phone="0533")

        channels, skipped = choose_channels(person, disabled={(person.user_id, Channel.SMS)})

        assert channels == [Channel.IN_APP]
        assert skipped == [(Channel.EMAIL, "no_address"), (Channel.SMS, "preference_off")]

    def test_a_channel_the_kind_does_not_use_is_neither_written_nor_counted(self) -> None:
        person = contact(uuid.uuid4(), UserRole.STUDENT, "Ali", email="a@example.com", phone="0532")

        channels, skipped = choose_channels(person, disabled=set(), allowed=IN_APP_AND_EMAIL)

        assert channels == [Channel.IN_APP, Channel.EMAIL]
        assert skipped == []


class FakeSmtp:
    """Stands in for smtplib.SMTP; `error` is raised when the message is sent."""

    error: Exception | None = None
    sent: list = []

    def __init__(self, host, port, timeout) -> None:
        self.address = (host, port)

    def __enter__(self):
        return self

    def __exit__(self, *_):
        return False

    def starttls(self) -> None: ...

    def login(self, username, password) -> None: ...

    def send_message(self, message) -> None:
        if FakeSmtp.error is not None:
            raise FakeSmtp.error
        FakeSmtp.sent.append(message)


class TestSmtpEmailSender:
    @pytest.fixture(autouse=True)
    def fake_smtp(self, monkeypatch):
        FakeSmtp.error, FakeSmtp.sent = None, []
        monkeypatch.setattr(smtplib, "SMTP", FakeSmtp)

    def test_sends_a_plain_text_message(self) -> None:
        SmtpEmailSender(Settings(mail_from="Dershane <no-reply@test>")).send("a@example.com", "Konu", "Gövde")

        message = FakeSmtp.sent[0]
        assert (message["To"], message["Subject"], message["From"]) == (
            "a@example.com",
            "Konu",
            "Dershane <no-reply@test>",
        )
        assert message.get_content().strip() == "Gövde"

    @pytest.mark.parametrize(
        ("error", "expected"),
        [
            (smtplib.SMTPRecipientsRefused({}), PermanentDeliveryError),
            (smtplib.SMTPResponseException(550, b"no such user"), PermanentDeliveryError),
            (smtplib.SMTPResponseException(421, b"busy"), TemporaryDeliveryError),
            (ConnectionRefusedError(), TemporaryDeliveryError),
        ],
    )
    def test_errors_are_classified(self, error, expected) -> None:
        FakeSmtp.error = error

        with pytest.raises(expected) as raised:
            SmtpEmailSender(Settings()).send("a@example.com", "Konu", "Gövde")

        assert "@" not in str(raised.value)  # stored in last_error and logged: no address
