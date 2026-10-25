import uuid
from datetime import date

import pandas as pd
import pytest

from app.scoring.ranking import rank_descending, rank_descending_within
from app.scoring.standard_score import (
    REASON_STATISTIC_MISSING,
    REASON_ZERO_STD_DEV,
    compute_scores,
    compute_statistics,
)
from app.scoring.weak_topics import TopicHierarchyCycleError, build_topic_ancestry, compute_topic_statuses

MATH, TURKISH = uuid.uuid4(), uuid.uuid4()
SCORE = uuid.uuid4()


def nets(rows: list[tuple[str, uuid.UUID, float]]) -> pd.DataFrame:
    return pd.DataFrame(rows, columns=["result_key", "subject_id", "net"])


WEIGHTS = pd.DataFrame(
    [(SCORE, MATH, 3.0), (SCORE, TURKISH, 2.0)], columns=["score_type_id", "subject_id", "weight"]
)
NETS = nets([("r1", MATH, 10), ("r1", TURKISH, 5), ("r2", MATH, 20), ("r2", TURKISH, 15)])


def test_statistics_use_population_standard_deviation() -> None:
    statistics = compute_statistics(NETS).set_index("subject_id")

    assert statistics.loc[MATH, "mean"] == 15
    assert statistics.loc[MATH, "std_dev"] == pytest.approx(5.0)
    assert statistics.loc[MATH, "participant_count"] == 2


def test_score_is_weighted_sum_of_standard_scores_plus_base() -> None:
    result = compute_scores(NETS, compute_statistics(NETS), WEIGHTS, {SCORE: 100.0})

    scores = result.scores.set_index("result_key")["estimated_score"]
    # r1 is one standard deviation below the mean in both tests: SP = 40 for each.
    assert scores["r1"] == pytest.approx(100 + 3 * 40 + 2 * 40)
    assert scores["r2"] == pytest.approx(100 + 3 * 60 + 2 * 60)
    assert result.skipped == []


def test_same_net_scores_higher_when_the_test_was_harder() -> None:
    easy = compute_statistics(nets([("x", MATH, 30), ("y", MATH, 20)]))
    hard = compute_statistics(nets([("x", MATH, 20), ("y", MATH, 10)]))
    weights = WEIGHTS[WEIGHTS["subject_id"] == MATH]
    student = nets([("me", MATH, 20)])

    easy_score = compute_scores(student, easy, weights, {}).scores["estimated_score"].iloc[0]
    hard_score = compute_scores(student, hard, weights, {}).scores["estimated_score"].iloc[0]
    assert hard_score > easy_score


def test_missing_statistics_leave_the_score_empty() -> None:
    statistics = compute_statistics(NETS)
    result = compute_scores(NETS, statistics[statistics["subject_id"] == MATH], WEIGHTS, {})

    assert result.scores["estimated_score"].isna().all()
    assert [(s.score_type_id, s.reason) for s in result.skipped] == [(SCORE, REASON_STATISTIC_MISSING)]
    assert result.skipped[0].subject_ids == (TURKISH,)


def test_zero_standard_deviation_leaves_the_score_empty() -> None:
    flat = nets([("r1", MATH, 10), ("r1", TURKISH, 5), ("r2", MATH, 10), ("r2", TURKISH, 15)])

    result = compute_scores(flat, compute_statistics(flat), WEIGHTS, {})

    assert result.scores["estimated_score"].isna().all()
    assert result.skipped[0].reason == REASON_ZERO_STD_DEV


def test_result_missing_a_subject_gets_no_score() -> None:
    partial = pd.concat([NETS, nets([("r3", MATH, 15)])])

    result = compute_scores(partial, compute_statistics(NETS), WEIGHTS, {})

    scores = result.scores.set_index("result_key")["estimated_score"]
    assert pd.isna(scores["r3"])
    assert result.incomplete_results == 1


def test_competition_ranking_keeps_ties_and_missing_values() -> None:
    ranks = rank_descending(pd.Series([10.0, 20.0, 20.0, None, 5.0]))

    assert ranks.tolist() == [3, 1, 1, pd.NA, 4]


def test_ranking_within_groups() -> None:
    frame = pd.DataFrame({"group": ["a", "a", "b"], "value": [1.0, 2.0, 1.0]})

    assert rank_descending_within(frame, "value", "group").tolist() == [2, 1, 1]


class TestWeakTopics:
    student = uuid.uuid4()
    tyt, ayt = uuid.uuid4(), uuid.uuid4()
    problems, age_problems, numbers = uuid.uuid4(), uuid.uuid4(), uuid.uuid4()

    def ancestry(self) -> pd.DataFrame:
        return build_topic_ancestry(
            {self.problems: None, self.age_problems: self.problems, self.numbers: None}
        )

    def result(
        self, exam: uuid.UUID, day: int, topic: uuid.UUID, questions: int, correct: int, exam_type=None
    ):
        return {
            "student_id": self.student,
            "exam_type_id": exam_type or self.tyt,
            "mock_exam_id": exam,
            "exam_date": date(2026, 10, day),
            "topic_id": topic,
            "question_count": questions,
            "correct": correct,
        }

    def test_only_the_last_n_exams_count(self) -> None:
        old, recent1, recent2 = uuid.uuid4(), uuid.uuid4(), uuid.uuid4()
        results = pd.DataFrame(
            [
                self.result(old, 1, self.numbers, 4, 0),
                self.result(recent1, 5, self.numbers, 4, 3),
                self.result(recent2, 9, self.numbers, 4, 4),
            ]
        )

        statuses = compute_topic_statuses(results, self.ancestry(), window_size=2, threshold=0.5)

        row = statuses.set_index("topic_id").loc[self.numbers]
        assert (row["total_questions"], row["total_correct"], row["measured_exam_count"]) == (8, 7, 2)
        assert bool(row["is_weak"]) is False

    def test_topic_below_threshold_is_weak(self) -> None:
        results = pd.DataFrame([self.result(uuid.uuid4(), 1, self.numbers, 10, 4)])

        statuses = compute_topic_statuses(results, self.ancestry(), window_size=3, threshold=0.5)

        assert bool(statuses.set_index("topic_id").loc[self.numbers, "is_weak"]) is True

    def test_subtopics_roll_up_to_their_parent(self) -> None:
        exam = uuid.uuid4()
        results = pd.DataFrame(
            [self.result(exam, 1, self.age_problems, 4, 1), self.result(exam, 1, self.problems, 6, 6)]
        )

        statuses = compute_topic_statuses(results, self.ancestry(), window_size=3, threshold=0.5).set_index(
            "topic_id"
        )

        assert statuses.loc[self.problems, "total_questions"] == 10
        assert statuses.loc[self.problems, "total_correct"] == 7
        assert bool(statuses.loc[self.age_problems, "is_weak"]) is True

    def test_window_is_counted_per_exam_type(self) -> None:
        tyt_exam, ayt_exam1, ayt_exam2 = uuid.uuid4(), uuid.uuid4(), uuid.uuid4()
        results = pd.DataFrame(
            [
                self.result(tyt_exam, 1, self.numbers, 4, 1),
                self.result(ayt_exam1, 5, self.problems, 4, 4, exam_type=self.ayt),
                self.result(ayt_exam2, 9, self.problems, 4, 4, exam_type=self.ayt),
            ]
        )

        statuses = compute_topic_statuses(results, self.ancestry(), window_size=1, threshold=0.5).set_index(
            "topic_id"
        )

        # The newer AYT exams do not push the only TYT exam out of the TYT window.
        assert statuses.loc[self.numbers, "measured_exam_count"] == 1
        assert statuses.loc[self.problems, "measured_exam_count"] == 1

    def test_no_results_gives_no_statuses(self) -> None:
        empty = pd.DataFrame(
            columns=[
                "student_id",
                "exam_type_id",
                "mock_exam_id",
                "exam_date",
                "topic_id",
                "question_count",
                "correct",
            ]
        )

        assert compute_topic_statuses(empty, self.ancestry(), window_size=3, threshold=0.5).empty

    def test_cycles_in_the_topic_hierarchy_are_rejected(self) -> None:
        a, b = uuid.uuid4(), uuid.uuid4()

        with pytest.raises(TopicHierarchyCycleError):
            build_topic_ancestry({a: b, b: a})
