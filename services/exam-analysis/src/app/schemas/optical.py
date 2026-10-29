import codecs

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator
from pydantic.alias_generators import to_camel

_CAMEL = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class FieldSlice(BaseModel):
    """A fixed-width column range; start is a 0-based character offset."""

    model_config = _CAMEL

    start: int = Field(ge=0)
    length: int = Field(gt=0)

    @property
    def end(self) -> int:
        return self.start + self.length


class OpticalFieldDefinition(BaseModel):
    """Layout of one optical reader export line (stored in optical_format.field_definition).

    The answer block may be split into segments (e.g. one per test on the form); segments are
    concatenated in order and question numbers run 1..question_count across them.
    Stored as camelCase JSON, like the rest of the API.
    """

    model_config = _CAMEL

    encoding: str = "utf-8"
    student_no: FieldSlice
    # Omitted for single-booklet forms; every row then uses default_booklet.
    booklet: FieldSlice | None = None
    default_booklet: str = Field(default="A", min_length=1, max_length=1)
    answer_segments: list[FieldSlice] = Field(min_length=1)
    # File id of the photographed sheet, written by the OMR service.
    sheet_ref: FieldSlice | None = None
    # Characters meaning "no answer". Any other character that is not the correct option is wrong,
    # so '*' (multiple marks on an OMR sheet) counts as a wrong answer.
    blank_chars: str = Field(default=" ", min_length=1)

    @field_validator("encoding")
    @classmethod
    def _known_encoding(cls, value: str) -> str:
        try:
            codecs.lookup(value)
        except LookupError as exc:
            raise ValueError(f"unknown encoding: {value}") from exc
        return value

    @field_validator("default_booklet")
    @classmethod
    def _upper_booklet(cls, value: str) -> str:
        return value.upper()

    @model_validator(mode="after")
    def _single_char_booklet(self) -> "OpticalFieldDefinition":
        if self.booklet is not None and self.booklet.length != 1:
            raise ValueError("booklet field must be exactly one character wide")
        return self

    @property
    def question_count(self) -> int:
        return sum(segment.length for segment in self.answer_segments)

    @property
    def identity_end(self) -> int:
        """Lines shorter than this cannot be attributed to a student and are malformed."""
        ends = [self.student_no.end]
        if self.booklet is not None:
            ends.append(self.booklet.end)
        if self.sheet_ref is not None:
            ends.append(self.sheet_ref.end)
        return max(ends)

    @property
    def line_length(self) -> int:
        return max(self.identity_end, *(segment.end for segment in self.answer_segments))
