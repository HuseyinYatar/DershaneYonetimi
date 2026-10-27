import functools
import operator
from dataclasses import dataclass

import pandas as pd

from app.schemas.optical import OpticalFieldDefinition

SHEET_COLUMNS = ["line_no", "raw_line", "student_no", "booklet", "answers", "sheet_ref", "is_malformed"]


# bu classı kullanma sebebimzi de birden fazla değer döndürüleceği zaman kullanılan yöntem
@dataclass(frozen=True)
class ParseOutcome:
    """sheets: one row per non-blank line, indexed by line_no, with SHEET_COLUMNS."""

    sheets: pd.DataFrame
    blank_lines_skipped: int
    short_lines_padded: int


def decode_lines(content: bytes, encoding: str) -> list[str]:
    """Raises UnicodeDecodeError when the file does not match the format's encoding."""
    return content.decode(encoding).splitlines()

# optikten okunup 0000001234ACDBADB sklinde gelen verinin nasıl parçalandığı anlatılıyor
def parse_lines(lines: list[str], definition: OpticalFieldDefinition) -> ParseOutcome:
    raw = pd.Series(lines, index=pd.RangeIndex(1, len(lines) + 1, name="line_no"), dtype="object")
    non_blank = raw[raw.str.strip() != ""]
    lengths = non_blank.str.len()

    is_malformed = lengths < definition.identity_end
    # Some exports trim trailing blanks; missing answer columns are treated as unanswered.
    is_short = ~is_malformed & (lengths < definition.line_length)
    padded = non_blank.str.ljust(definition.line_length)

    student_no = padded.str.slice(definition.student_no.start, definition.student_no.end).str.strip()
    if definition.booklet is None:
        booklet = pd.Series(definition.default_booklet, index=padded.index, dtype="object")
    else:
        booklet = padded.str.slice(definition.booklet.start, definition.booklet.end).str.strip().str.upper()
    answers = functools.reduce(
        operator.add,
        (padded.str.slice(segment.start, segment.end) for segment in definition.answer_segments),
    )
    if definition.sheet_ref is None:
        sheet_ref = pd.Series(None, index=padded.index, dtype="object")
    else:
        sheet_ref = padded.str.slice(definition.sheet_ref.start, definition.sheet_ref.end).str.strip()
        sheet_ref = sheet_ref.where(sheet_ref != "", None)

    sheets = pd.DataFrame(
        {
            "line_no": non_blank.index.to_numpy(),
            "raw_line": non_blank.to_numpy(),
            "student_no": student_no.to_numpy(),
            "booklet": booklet.to_numpy(),
            "answers": answers.to_numpy(),
            "sheet_ref": sheet_ref.to_numpy(),
            "is_malformed": is_malformed.to_numpy(),
        },
        index=non_blank.index,
    )
    return ParseOutcome(
        sheets=sheets,
        blank_lines_skipped=int(len(raw) - len(non_blank)),
        short_lines_padded=int(is_short.sum()),
    )
