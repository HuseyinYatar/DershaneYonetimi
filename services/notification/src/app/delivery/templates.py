"""Turkish notification texts, rendered from the Jinja2 files under app/templates/.

Templates live in the repository (not in a table): a text change goes through a PR, the template
test renders every file, and the text is versioned with the code. Layout:
    templates/<template set>/<audience>.<part>.txt    part = title | body | sms
"""

from dataclasses import dataclass
from datetime import date, datetime, time
from pathlib import Path
from typing import Any, Literal
from zoneinfo import ZoneInfo

from jinja2 import Environment, FileSystemLoader, StrictUndefined, select_autoescape

from app.delivery.kinds import KINDS
from app.models.enums import Channel

Audience = Literal["student", "guardian"]

TEMPLATE_DIR = Path(__file__).resolve().parent.parent / "templates"

# notification.title is String(200).
TITLE_MAX_LENGTH = 200

# event type -> template folder (app.delivery.kinds is the source).
TEMPLATE_SETS = {event_type: kind.template_set for event_type, kind in KINDS.items()}

_MONTHS = ("Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
           "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık")  # fmt: skip

# Plain text: nothing is HTML-escaped. StrictUndefined turns a missing variable into an error, so a
# typo in a template fails the template test instead of sending an empty gap.
_environment = Environment(
    loader=FileSystemLoader(TEMPLATE_DIR),
    undefined=StrictUndefined,
    autoescape=select_autoescape(enabled_extensions=("html",)),
    keep_trailing_newline=False,
)


@dataclass(frozen=True)
class RenderedText:
    title: str
    body: str


def turkish_date(value: date | None) -> str | None:
    return None if value is None else f"{value.day} {_MONTHS[value.month - 1]} {value.year}"


def clock_time(value: time) -> str:
    """10:00:00 -> "10:00". Attendance times are already the institution's local time."""
    return value.strftime("%H:%M")


def turkish_datetime(value: datetime, time_zone: ZoneInfo) -> str:
    """An instant (task deadlines arrive in UTC) in the institution's time zone:
    2026-11-16T20:59:00Z -> "16 Kasım 2026 23:59" for Europe/Istanbul."""
    local = value.astimezone(time_zone)
    return f"{turkish_date(local.date())} {clock_time(local.time())}"


def render(event_type: str, audience: Audience, channel: Channel, values: dict[str, Any]) -> RenderedText:
    folder = TEMPLATE_SETS[event_type]

    def part(name: str) -> str:
        return _environment.get_template(f"{folder}/{audience}.{name}.txt").render(values).strip()

    # SMS has its own short text; the title is still stored for the record.
    body = part("sms") if channel is Channel.SMS else part("body")
    return RenderedText(title=shorten(part("title"), TITLE_MAX_LENGTH), body=body)


def shorten(text: str, limit: int) -> str:
    """Titles carry free text (a task title may be 200 characters on its own); a title longer than
    the column would fail the whole event, so it is cut and ends with an ellipsis."""
    return text if len(text) <= limit else text[: limit - 1].rstrip() + "…"
