import uuid
from collections.abc import Collection, Sequence

import pandas as pd

from app.models.enums import AnswerStatus

COUNT_COLUMNS = ["correct", "wrong", "blank"]
ANSWERED = {AnswerStatus.CORRECT.value, AnswerStatus.WRONG.value}


def drop_untaken_tests(graded: pd.DataFrame, tests: Sequence[Collection[uuid.UUID]]) -> pd.DataFrame:
    """Removes the answers of optional tests a sheet did not take (AYT).

    tests: subject ids per ÖSYM test. A test in which the sheet has no correct or wrong answer was
    not taken: its questions produce no subject or topic result and no blanks. Subjects outside the
    tests (and every subject when tests is empty) are kept.
    """
    if not tests or graded.empty:
        return graded
    test_of_subject = {subject_id: number for number, test in enumerate(tests) for subject_id in test}
    test = graded["subject_id"].map(test_of_subject)
    answered = graded["status"].isin(ANSWERED)
    taken = answered.groupby([graded["sheet_key"], test]).transform("any")
    return graded[test.isna() | taken.fillna(True).astype(bool)]


def aggregate_subjects(graded: pd.DataFrame, wrong_divisor: float) -> pd.DataFrame:
    """Per sheet and subject: correct, wrong, blank (cancelled excluded) and net = C - W / divisor."""
    counted = graded.assign(
        correct=graded["status"].eq(AnswerStatus.CORRECT.value),
        wrong=graded["status"].eq(AnswerStatus.WRONG.value),
        blank=graded["status"].eq(AnswerStatus.BLANK.value),
    )
    subjects = counted.groupby(["sheet_key", "subject_id"], sort=False)[COUNT_COLUMNS].sum().reset_index()
    subjects["net"] = subjects["correct"] - subjects["wrong"] / wrong_divisor
    return subjects


def aggregate_topics(graded: pd.DataFrame) -> pd.DataFrame:
    """Per sheet and topic: question_count and correct over non-cancelled questions, success_rate."""
    scored = graded[graded["status"] != AnswerStatus.CANCELLED.value]
    topics = (
        scored.assign(correct=scored["status"].eq(AnswerStatus.CORRECT.value))
        .groupby(["sheet_key", "topic_id"], sort=False)
        .agg(question_count=("status", "size"), correct=("correct", "sum"))
        .reset_index()
    )
    topics["success_rate"] = topics["correct"] / topics["question_count"]
    return topics


def aggregate_totals(subjects: pd.DataFrame) -> pd.DataFrame:
    """Per sheet totals; the total net is the sum of the subject nets."""
    return subjects.groupby("sheet_key", sort=False)[[*COUNT_COLUMNS, "net"]].sum().reset_index()
