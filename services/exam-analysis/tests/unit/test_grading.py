import uuid

import pandas as pd
import pytest

from app.models.enums import AnswerStatus
from app.scoring.aggregation import aggregate_subjects, aggregate_topics, aggregate_totals
from app.scoring.grader import explode_answers, grade, regrade

MATH, TURKISH = uuid.uuid4(), uuid.uuid4()
NUMBERS, PROBLEMS, PARAGRAPH = uuid.uuid4(), uuid.uuid4(), uuid.uuid4()


def answer_key(cancelled: frozenset[int] = frozenset()) -> pd.DataFrame:
    """Booklet A: questions 1-3 math, 4-5 Turkish. Booklet B lists the same questions reordered."""
    layout = {
        "A": [
            (MATH, NUMBERS, "A"),
            (MATH, NUMBERS, "B"),
            (MATH, PROBLEMS, "C"),
            (TURKISH, PARAGRAPH, "D"),
            (TURKISH, PARAGRAPH, "E"),
        ],
        "B": [
            (TURKISH, PARAGRAPH, "E"),
            (MATH, PROBLEMS, "C"),
            (MATH, NUMBERS, "A"),
            (TURKISH, PARAGRAPH, "D"),
            (MATH, NUMBERS, "B"),
        ],
    }
    rows = [
        {
            "answer_key_item_id": uuid.uuid4(),
            "booklet": booklet,
            "question_no": number,
            "subject_id": subject,
            "topic_id": topic,
            "correct_answer": correct,
            "is_cancelled": booklet == "A" and number in cancelled,
        }
        for booklet, questions in layout.items()
        for number, (subject, topic, correct) in enumerate(questions, start=1)
    ]
    return pd.DataFrame(rows)


def sheets(*rows: tuple[str, str]) -> pd.DataFrame:
    return pd.DataFrame([{"booklet": b, "answers": a} for b, a in rows], index=range(1, len(rows) + 1))


def graded_for(*rows: tuple[str, str], cancelled: frozenset[int] = frozenset()) -> pd.DataFrame:
    return grade(explode_answers(sheets(*rows), 5, " "), answer_key(cancelled))


def statuses(graded: pd.DataFrame, sheet: int) -> list[str]:
    return graded[graded["sheet_key"] == sheet].sort_values("question_no")["status"].tolist()


def test_each_answer_gets_a_status() -> None:
    graded = graded_for(("A", "AB*d "))

    assert statuses(graded, 1) == ["CORRECT", "CORRECT", "WRONG", "CORRECT", "BLANK"]


def test_lowercase_answers_are_accepted_and_blanks_have_no_answer() -> None:
    ordered = graded_for(("A", "a    ")).sort_values("question_no")

    assert ordered.iloc[0]["given_answer"] == "A"
    assert pd.isna(ordered.iloc[1]["given_answer"])


def test_booklets_are_graded_against_their_own_key() -> None:
    graded = graded_for(("A", "ABCDE"), ("B", "ECADB"))

    assert statuses(graded, 1) == ["CORRECT"] * 5
    assert statuses(graded, 2) == ["CORRECT"] * 5


def test_cancelled_question_is_cancelled_whatever_was_marked() -> None:
    graded = graded_for(("A", "XBCDE"), cancelled=frozenset({1}))

    assert statuses(graded, 1)[0] == AnswerStatus.CANCELLED.value


def test_answers_beyond_the_key_are_ignored() -> None:
    long = explode_answers(pd.DataFrame([{"booklet": "A", "answers": "ABCDEAAAA"}], index=[1]), 9, " ")

    assert len(grade(long, answer_key())) == 5


@pytest.mark.parametrize(("divisor", "expected_math_net"), [(4.0, 1.75), (3.0, 5 / 3)])
def test_net_uses_the_configured_wrong_divisor(divisor: float, expected_math_net: float) -> None:
    subjects = aggregate_subjects(graded_for(("A", "ABE  ")), divisor)

    math = subjects[subjects["subject_id"] == MATH].iloc[0]
    assert (math["correct"], math["wrong"], math["blank"]) == (2, 1, 0)
    assert math["net"] == pytest.approx(expected_math_net)


def test_cancelled_questions_are_left_out_of_counts_and_topics() -> None:
    graded = graded_for(("A", "XBCDE"), cancelled=frozenset({1}))

    math = aggregate_subjects(graded, 4.0).query("subject_id == @MATH").iloc[0]
    numbers = aggregate_topics(graded).query("topic_id == @NUMBERS").iloc[0]
    assert (math["correct"], math["wrong"], math["blank"]) == (2, 0, 0)
    assert (numbers["question_count"], numbers["correct"]) == (1, 1)


def test_topic_success_rate() -> None:
    topics = aggregate_topics(graded_for(("A", "A CDE")))

    numbers = topics[topics["topic_id"] == NUMBERS].iloc[0]
    assert numbers["success_rate"] == pytest.approx(0.5)


def test_totals_sum_subject_nets() -> None:
    totals = aggregate_totals(aggregate_subjects(graded_for(("A", "ABEDA")), 4.0))

    assert totals.iloc[0][["correct", "wrong", "blank"]].tolist() == [3, 2, 0]
    assert totals.iloc[0]["net"] == pytest.approx(2.5)


def test_regrade_applies_a_new_cancellation_to_stored_answers() -> None:
    key = answer_key(cancelled=frozenset({3}))
    item_for_q3 = key[(key["booklet"] == "A") & (key["question_no"] == 3)]["answer_key_item_id"].iloc[0]
    stored = pd.DataFrame([{"sheet_key": "result-1", "answer_key_item_id": item_for_q3, "given_answer": "A"}])

    regraded = regrade(stored, key)

    assert regraded["status"].tolist() == [AnswerStatus.CANCELLED.value]
