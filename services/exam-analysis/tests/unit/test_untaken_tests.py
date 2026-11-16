"""AYT optional tests: a test the sheet answered nothing in leaves no results and no blanks."""

import uuid

import pandas as pd

from app.scoring.aggregation import aggregate_subjects, aggregate_topics, aggregate_totals, drop_untaken_tests

EDB, TAR1, MAT, FIZ, KIM = (uuid.uuid4() for _ in range(5))
TOPIC = uuid.uuid4()
# TDE-SB1 (Edebiyat + Tarih-1), Matematik, Fen Bilimleri (Fizik + Kimya)
TESTS = [{EDB, TAR1}, {MAT}, {FIZ, KIM}]


def graded(rows: list[tuple[str, uuid.UUID, str]]) -> pd.DataFrame:
    return pd.DataFrame(
        [(sheet, subject, TOPIC, status) for sheet, subject, status in rows],
        columns=["sheet_key", "subject_id", "topic_id", "status"],
    )


SAY_SHEET = [
    ("say", EDB, "BLANK"), ("say", EDB, "BLANK"), ("say", TAR1, "BLANK"),  # TDE-SB1 not taken
    ("say", MAT, "CORRECT"), ("say", MAT, "WRONG"), ("say", MAT, "BLANK"),
    ("say", FIZ, "CORRECT"), ("say", KIM, "BLANK"),  # Fen taken: Kimya's blank counts
]  # fmt: skip


def test_an_untaken_test_leaves_no_subjects_and_no_blanks() -> None:
    kept = drop_untaken_tests(graded(SAY_SHEET), TESTS)
    subjects = aggregate_subjects(kept, 4)
    totals = aggregate_totals(subjects)

    assert set(subjects["subject_id"]) == {MAT, FIZ, KIM}
    assert totals.iloc[0][["correct", "wrong", "blank"]].tolist() == [2, 1, 2]


def test_one_answered_subject_keeps_the_whole_test() -> None:
    kept = drop_untaken_tests(graded(SAY_SHEET), TESTS)

    assert (kept["subject_id"] == KIM).any()  # Kimya is blank, but Fizik of the same test is answered


def test_untaken_tests_leave_no_topic_results() -> None:
    kept = drop_untaken_tests(graded([("x", EDB, "BLANK"), ("x", MAT, "CORRECT")]), TESTS)

    topics = aggregate_topics(kept)

    assert topics.iloc[0]["question_count"] == 1  # only the Matematik question


def test_without_optional_tests_every_subject_counts() -> None:
    frame = graded(SAY_SHEET)

    assert drop_untaken_tests(frame, []).equals(frame)
