import smtplib
import uuid
from datetime import date

import pytest

from app.config import Settings
from app.delivery.planner import ExamInfo, choose_channels
from app.delivery.senders import PermanentDeliveryError, SmtpEmailSender, TemporaryDeliveryError
from app.delivery.templates import TEMPLATE_SETS, render, turkish_date
from app.models.enums import Channel, UserRole
from tests.factories import contact

VALUES = {
    "recipient_first_name": "Fatma",
    "student_full_name": "Ali Yılmaz",
    "exam_name": "TYT Deneme 3",
    "exam_date": "12 Ekim 2026",
    "link": "http://frontend/students/1/results/2",
}


class TestTemplates:
    @pytest.mark.parametrize("event_type", sorted(TEMPLATE_SETS))
    @pytest.mark.parametrize("audience", ["student", "guardian"])
    @pytest.mark.parametrize("channel", list(Channel))
    def test_every_template_renders(self, event_type, audience, channel) -> None:
        text = render(event_type, audience, channel, VALUES)

        assert "TYT Deneme 3" in text.title
        assert VALUES["link"] in text.body

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
