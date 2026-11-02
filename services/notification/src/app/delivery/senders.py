"""Outgoing channels. Each sender raises TemporaryDeliveryError (try again later) or
PermanentDeliveryError (trying again cannot help); the dispatcher decides what happens next."""

import smtplib
from email.message import EmailMessage
from typing import Protocol

import structlog

from app.config import Settings

logger = structlog.get_logger(__name__)


class DeliveryError(Exception):
    """The message carries the error type or SMTP code only, never an address: it is stored and logged."""


class TemporaryDeliveryError(DeliveryError):
    pass


class PermanentDeliveryError(DeliveryError):
    pass


class EmailSender(Protocol):
    def send(self, to: str, subject: str, body: str) -> None: ...


class SmsSender(Protocol):
    def send(self, to: str, text: str) -> None: ...


class SmtpEmailSender:
    """Same code everywhere, only the server differs: Mailpit locally (catches every mail, UI on
    :8025), the SES SMTP endpoint with STARTTLS and credentials on AWS."""

    def __init__(self, settings: Settings) -> None:
        self._settings = settings

    def send(self, to: str, subject: str, body: str) -> None:
        settings = self._settings
        message = EmailMessage()
        message["From"] = settings.mail_from
        message["To"] = to
        message["Subject"] = subject
        message.set_content(body)  # plain text, UTF-8
        try:
            with smtplib.SMTP(
                settings.smtp_host, settings.smtp_port, timeout=settings.smtp_timeout_seconds
            ) as smtp:
                if settings.smtp_starttls:
                    smtp.starttls()
                if settings.smtp_username:
                    smtp.login(settings.smtp_username, settings.smtp_password or "")
                smtp.send_message(message)
        except smtplib.SMTPRecipientsRefused as exc:
            raise PermanentDeliveryError("SMTPRecipientsRefused") from exc
        except smtplib.SMTPResponseException as exc:
            # 5xx: the server rejected the message for good; 4xx: busy or throttled, try again.
            error = PermanentDeliveryError if exc.smtp_code >= 500 else TemporaryDeliveryError
            raise error(f"SMTP {exc.smtp_code}") from exc
        except (smtplib.SMTPException, OSError) as exc:
            # Server down, connection refused, timeout.
            raise TemporaryDeliveryError(type(exc).__name__) from exc


class LoggingSmsSender:
    """Placeholder until an SMS provider is chosen: sends nothing and logs that it did not.
    The number and the text are not logged (KVKK); the dispatcher logs the notification id.
    """

    def send(self, to: str, text: str) -> None:
        logger.info("sms.not_sent", reason="no_sms_provider", characters=len(text))
