"""Test-based standard scores, as ÖSYM and MEB compute them.

standard score = 50 + 10 * (net - mean) / std_dev, per subject (test); the estimated score of a
score type is the weighted sum of its subjects' standard scores plus the score type's base score.
How that sum is finally scaled is an open decision and is isolated in _apply_base_score.
"""

import uuid
from collections.abc import Mapping
from dataclasses import dataclass, field

import numpy as np
import pandas as pd

# Definition of a standard (T) score, not tunable configuration.
STANDARD_SCORE_MEAN = 50.0
STANDARD_SCORE_SPREAD = 10.0

REASON_STATISTIC_MISSING = "statistic_missing"
REASON_ZERO_STD_DEV = "zero_std_dev"


def compute_statistics(subject_nets: pd.DataFrame) -> pd.DataFrame:
    """subject_nets: subject_id, net. Population standard deviation (ddof=0)."""
    nets = subject_nets.groupby("subject_id")["net"]
    return pd.DataFrame(
        {"mean": nets.mean(), "std_dev": nets.std(ddof=0), "participant_count": nets.size()}
    ).reset_index()


@dataclass(frozen=True)
class SkippedScoreType:
    score_type_id: uuid.UUID
    reason: str
    subject_ids: tuple[uuid.UUID, ...]


@dataclass(frozen=True)
class ScoreComputation:
    """scores: result_key, score_type_id, estimated_score (NaN when it cannot be computed)."""

    scores: pd.DataFrame
    skipped: list[SkippedScoreType] = field(default_factory=list)
    # Results lacking one of a score type's subjects; their score for that type stays NaN.
    incomplete_results: int = 0


def compute_scores(subject_nets: pd.DataFrame, statistics: pd.DataFrame, weights: pd.DataFrame,base_scores: Mapping[uuid.UUID, float | None],) -> ScoreComputation:
    """subject_nets: result_key, subject_id, net; statistics: subject_id, mean, std_dev;
    weights: score_type_id, subject_id, weight. Every (result, score type) pair is returned.
    """
    weighted_stats = weights.merge(statistics[["subject_id", "mean", "std_dev"]], on="subject_id", how="left")
    skipped = _skipped_score_types(weighted_stats)
    skipped_ids = {item.score_type_id for item in skipped}
    usable = weighted_stats[~weighted_stats["score_type_id"].isin(skipped_ids)]

    all_pairs = pd.MultiIndex.from_product(
        [subject_nets["result_key"].unique(), weights["score_type_id"].unique()],
        names=["result_key", "score_type_id"],
    ).to_frame(index=False)

    contributions = subject_nets.merge(usable, on="subject_id", how="inner")
    contributions["weighted"] = (
        STANDARD_SCORE_MEAN
        + STANDARD_SCORE_SPREAD * (contributions["net"] - contributions["mean"]) / contributions["std_dev"]
    ) * contributions["weight"]
    sums = (
        contributions.groupby(["result_key", "score_type_id"])
        .agg(weighted=("weighted", "sum"), subject_count=("subject_id", "nunique"))
        .reset_index()
    )
    expected = usable.groupby("score_type_id")["subject_id"].nunique().rename("expected_count").reset_index()
    sums = sums.merge(expected, on="score_type_id", how="left")
    is_complete = sums["subject_count"] == sums["expected_count"]
    sums["estimated_score"] = np.where(is_complete, _apply_base_score(sums, base_scores), np.nan)

    scores = all_pairs.merge(sums[["result_key", "score_type_id", "estimated_score"]], how="left")
    return ScoreComputation(scores=scores, skipped=skipped, incomplete_results=int((~is_complete).sum()))


def _apply_base_score(sums: pd.DataFrame, base_scores: Mapping[uuid.UUID, float | None]) -> pd.Series:
    lookup = pd.Series(
        {score_type_id: float(base or 0.0) for score_type_id, base in base_scores.items()}, dtype=float
    )
    return sums["weighted"] + sums["score_type_id"].map(lookup).fillna(0.0)


def _skipped_score_types(weighted_stats: pd.DataFrame) -> list[SkippedScoreType]:
    missing = weighted_stats[weighted_stats["mean"].isna()]
    zero_spread = weighted_stats[weighted_stats["std_dev"].notna() & (weighted_stats["std_dev"] <= 0)]
    skipped: dict[uuid.UUID, SkippedScoreType] = {}
    for reason, rows in ((REASON_STATISTIC_MISSING, missing), (REASON_ZERO_STD_DEV, zero_spread)):
        for score_type_id, group in rows.groupby("score_type_id"):
            skipped.setdefault(
                score_type_id,
                SkippedScoreType(score_type_id, reason, tuple(group["subject_id"].tolist())),
            )
    return list(skipped.values())
