"""Turkish notification texts, rendered from the Jinja2 files under app/templates/.

Templates live in the repository (not in a table): a text change goes through a PR, the template
test renders every file, and the text is versioned with the code. Layout:
    templates/<template set>/<audience>.<part>.txt    part = title | body | sms
"""

from dataclasses import dataclass
from datetime import date
from pathlib import Path
from typing import Any, Literal

from jinja2 import Environment, FileSystemLoader, StrictUndefined, select_autoescape

from app.models.enums import Channel

Audience = Literal["student", "guardian"]

TEMPLATE_DIR = Path(__file__).resolve().parent.parent / "templates"

# Both exam events tell the same thing to the student and the guardian.
TEMPLATE_SETS = {"ExamResultsPublished": "exam_result", "StudentResultAdded": "exam_result"}

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


def render(event_type: str, audience: Audience, channel: Channel, values: dict[str, Any]) -> RenderedText:
    folder = TEMPLATE_SETS[event_type]

    def part(name: str) -> str:
        return _environment.get_template(f"{folder}/{audience}.{name}.txt").render(values).strip()

    # SMS has its own short text; the title is still stored for the record.
    body = part("sms") if channel is Channel.SMS else part("body")
    return RenderedText(title=part("title"), body=body)
