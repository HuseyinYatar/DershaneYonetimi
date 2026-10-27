import uuid
from collections.abc import Mapping

import pandas as pd

STATUS_COLUMNS = [
    "student_id",
    "topic_id",
    "total_questions",
    "total_correct",
    "measured_exam_count",
    "success_rate",
    "is_weak",
]


class TopicHierarchyCycleError(ValueError):
    pass


def build_topic_ancestry(parents: Mapping[uuid.UUID, uuid.UUID | None]) -> pd.DataFrame:
    """Maps every topic to itself and each of its ancestors (topic_id, rollup_topic_id).

    Iterates over the topic catalog (hundreds of rows), not over student data.
    """
    pairs: list[tuple[uuid.UUID, uuid.UUID]] = []
    for topic_id in parents:
        current: uuid.UUID | None = topic_id
        seen: set[uuid.UUID] = set()
        while current is not None:
            if current in seen:
                raise TopicHierarchyCycleError(f"topic hierarchy has a cycle at {current}")
            seen.add(current)
            pairs.append((topic_id, current))
            current = parents.get(current)
    return pd.DataFrame(pairs, columns=["topic_id", "rollup_topic_id"])


def compute_topic_statuses( topic_results: pd.DataFrame, ancestry: pd.DataFrame, window_size: int, threshold: float) -> pd.DataFrame:
    """Weak topic summary over each student's last `window_size` exams of the same exam type.
    topic_results: student_id, exam_type_id, mock_exam_id, exam_date, topic_id, question_count,
    correct — only published exams. Results are rolled up to parent topics. A topic is weak
    when its success rate over the window is below the threshold.
    """
    if topic_results.empty:
        return pd.DataFrame(columns=STATUS_COLUMNS)

    exam_keys = ["student_id", "exam_type_id"]
    exams = (
        topic_results[[*exam_keys, "mock_exam_id", "exam_date"]]
        .drop_duplicates()
        .sort_values([*exam_keys, "exam_date", "mock_exam_id"], ascending=[True, True, False, False])
    )
    exams["recency"] = exams.groupby(exam_keys).cumcount()
    window = exams.loc[exams["recency"] < window_size, ["student_id", "mock_exam_id"]]

    in_window = topic_results.merge(window, on=["student_id", "mock_exam_id"])
    rolled_up = in_window.merge(ancestry, on="topic_id")
    statuses = (
        rolled_up.groupby(["student_id", "rollup_topic_id"])
        .agg(
            total_questions=("question_count", "sum"),
            total_correct=("correct", "sum"),
            measured_exam_count=("mock_exam_id", "nunique"),
        )
        .reset_index()
        .rename(columns={"rollup_topic_id": "topic_id"})
    )
    statuses = statuses[statuses["total_questions"] > 0].copy()
    statuses["success_rate"] = statuses["total_correct"] / statuses["total_questions"]
    statuses["is_weak"] = statuses["success_rate"] < threshold
    return statuses[STATUS_COLUMNS]


