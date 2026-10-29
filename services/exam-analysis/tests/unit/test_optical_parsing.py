import uuid

import pandas as pd
import pytest
from pydantic import ValidationError

from app.models.enums import UnmatchedReason
from app.schemas.optical import OpticalFieldDefinition
from app.scoring.optical_parser import decode_lines, parse_lines
from app.scoring.row_matching import classify_rows

DEFINITION = OpticalFieldDefinition.model_validate(
    {
        "studentNo": {"start": 0, "length": 4},
        "booklet": {"start": 4, "length": 1},
        "answerSegments": [{"start": 5, "length": 3}, {"start": 9, "length": 2}],
    }
)


def test_definition_reports_question_count_and_line_lengths() -> None:
    assert DEFINITION.question_count == 5
    assert DEFINITION.identity_end == 5
    assert DEFINITION.line_length == 11


def test_segments_are_concatenated_in_order() -> None:
    sheets = parse_lines(["1001AABC|DE"], DEFINITION).sheets

    assert sheets.loc[1, "answers"] == "ABCDE"
    assert sheets.loc[1, "student_no"] == "1001"
    assert sheets.loc[1, "booklet"] == "A"


def test_blank_lines_are_skipped_and_counted() -> None:
    outcome = parse_lines(["1001AABC DE", "", "   ", "1002BEDCBA "], DEFINITION)

    assert list(outcome.sheets["line_no"]) == [1, 4]
    assert outcome.blank_lines_skipped == 2


def test_lines_without_identity_are_malformed() -> None:
    sheets = parse_lines(["10"], DEFINITION).sheets

    assert bool(sheets.loc[1, "is_malformed"]) is True


def test_trimmed_trailing_answers_are_padded_as_blank() -> None:
    outcome = parse_lines(["1001AAB"], DEFINITION)

    assert outcome.sheets.loc[1, "answers"] == "AB   "
    assert outcome.short_lines_padded == 1
    assert bool(outcome.sheets.loc[1, "is_malformed"]) is False


def test_single_booklet_forms_use_the_default_booklet() -> None:
    definition = OpticalFieldDefinition.model_validate(
        {
            "studentNo": {"start": 0, "length": 4},
            "answerSegments": [{"start": 4, "length": 2}],
            "defaultBooklet": "a",
        }
    )

    assert parse_lines(["1001AB"], definition).sheets.loc[1, "booklet"] == "A"


def test_sheet_ref_is_read_for_omr_rows() -> None:
    sheet_id = uuid.uuid4()
    definition = OpticalFieldDefinition.model_validate(
        {
            "studentNo": {"start": 0, "length": 4},
            "booklet": {"start": 4, "length": 1},
            "sheetRef": {"start": 5, "length": 36},
            "answerSegments": [{"start": 41, "length": 2}],
        }
    )

    sheets = parse_lines([f"1001A{sheet_id}AB", f"1002A{' ' * 36}AB"], definition).sheets

    assert sheets.loc[1, "sheet_ref"] == str(sheet_id)
    assert pd.isna(sheets.loc[2, "sheet_ref"])


def test_windows_1254_files_decode_turkish_characters() -> None:
    assert decode_lines("1001AĞÜŞ\r\n".encode("windows-1254"), "windows-1254") == ["1001AĞÜŞ"]


def test_wrong_encoding_raises() -> None:
    with pytest.raises(UnicodeDecodeError):
        decode_lines("Ğ".encode("windows-1254"), "utf-8")


def test_booklet_field_must_be_one_character() -> None:
    with pytest.raises(ValidationError):
        OpticalFieldDefinition.model_validate(
            {
                "studentNo": {"start": 0, "length": 4},
                "booklet": {"start": 4, "length": 2},
                "answerSegments": [{"start": 6, "length": 1}],
            }
        )


def test_unknown_encoding_is_rejected() -> None:
    with pytest.raises(ValidationError):
        OpticalFieldDefinition.model_validate(
            {
                "encoding": "klingon",
                "studentNo": {"start": 0, "length": 4},
                "answerSegments": [{"start": 4, "length": 1}],
            }
        )


class TestClassifyRows:
    def setup_method(self) -> None:
        self.ali, self.ayse, self.can = uuid.uuid4(), uuid.uuid4(), uuid.uuid4()
        self.ids = pd.Series({"1001": self.ali, "1002": self.ayse, "1003": self.can}, dtype=object)

    def classify(self, lines: list[str], existing: set[uuid.UUID] | None = None):
        sheets = parse_lines(lines, DEFINITION).sheets
        return classify_rows(
            sheets,
            valid_booklets={"A", "B"},
            student_ids_by_no=self.ids,
            existing_student_ids=existing or set(),
        )

    def test_known_students_are_matched(self) -> None:
        result = self.classify(["1001AABCDE ", "1002BABCDE "])

        assert list(result.matched["student_id"]) == [self.ali, self.ayse]
        assert result.unmatched.empty

    def test_each_reason_is_detected(self) -> None:
        result = self.classify(["99", "1001CABCDE ", "9999AABCDE ", "1002AABCDE ", "1002AABCDE "])

        reasons = dict(zip(result.unmatched["line_no"], result.unmatched["reason"], strict=True))
        assert reasons == {
            1: UnmatchedReason.MALFORMED_LINE.value,
            2: UnmatchedReason.INVALID_BOOKLET.value,
            3: UnmatchedReason.STUDENT_NOT_FOUND.value,
            5: UnmatchedReason.DUPLICATE_STUDENT.value,
        }
        assert list(result.matched["line_no"]) == [4]

    def test_students_with_an_existing_result_are_duplicates(self) -> None:
        result = self.classify(["1003AABCDE "], existing={self.can})

        assert list(result.unmatched["reason"]) == [UnmatchedReason.DUPLICATE_STUDENT.value]

    def test_invalid_row_does_not_shadow_a_later_valid_row_of_the_same_student(self) -> None:
        result = self.classify(["1001CABCDE ", "1001AABCDE "])

        assert list(result.matched["line_no"]) == [2]
        assert list(result.unmatched["reason"]) == [UnmatchedReason.INVALID_BOOKLET.value]
