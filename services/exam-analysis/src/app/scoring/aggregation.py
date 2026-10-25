import pandas as pd

from app.models.enums import AnswerStatus

COUNT_COLUMNS = ["correct", "wrong", "blank"]


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
