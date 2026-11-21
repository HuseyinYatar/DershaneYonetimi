"""Estimated scores from per-net coefficients (the way public YKS calculators estimate ÖSYM scores).

ÖSYM's chain (net -> standard score -> weighted sum -> linear 100-500 scaling) is linear in the nets
once the national means, standard deviations and scaling bounds are fixed. With last year's values
fixed, the whole chain collapses to

    score = constant + sum(coefficient * max(net, 0)),  capped at the score type's maximum (500)

The constant ("score at zero net") and the coefficients ("points per net") come from the catalog
(seed/catalog.json) with the year they estimate. A test's negative net counts as 0, as ÖSYM does.

A score type is only computed when at least one of its minimum-net groups reaches the minimum net
(0.5): TYT needs Türkçe or Temel Matematik; SAY needs AYT Matematik or the Fen Bilimleri test
(Fizik + Kimya + Biyoloji) and so on. A group is summed, because the rule applies to an ÖSYM test
and some tests are split into several subjects here.

A subject without a result is a test the student did not take (AYT optional tests leave no subject
results): it counts as 0 net, as ÖSYM counts an untaken test.
"""

import uuid
from collections.abc import Mapping
from dataclasses import dataclass

import numpy as np
import pandas as pd

REASON_MINIMUM_NET_NOT_MET = "MINIMUM_NET_NOT_MET"


@dataclass(frozen=True)
class CoefficientRule:
    """Per score type: constant, maximum score and the net a minimum-net group has to reach."""

    constant: float
    max_score: float
    minimum_net: float


def compute_coefficient_scores(
    subject_nets: pd.DataFrame, coefficients: pd.DataFrame, rules: Mapping[uuid.UUID, CoefficientRule]
) -> pd.DataFrame:
    """subject_nets: result_key, subject_id, net (raw, may be negative).
    coefficients: score_type_id, subject_id, coefficient, minimum_net_group (nullable int).

    Returns one row per (result, score type): result_key, score_type_id, estimated_score (NaN when
    not computed) and not_computed_reason (None when computed).
    """
    columns = ["result_key", "score_type_id", "estimated_score", "not_computed_reason"]
    if subject_nets.empty or coefficients.empty:
        return pd.DataFrame(columns=columns)

    pairs = pd.MultiIndex.from_product(
        [subject_nets["result_key"].unique(), coefficients["score_type_id"].unique()],
        names=["result_key", "score_type_id"],
    ).to_frame(index=False)

    # Every (result, score type, subject) the score type needs; an untaken test has 0 net.
    needed = pairs.merge(coefficients, on="score_type_id").merge(
        subject_nets, on=["result_key", "subject_id"], how="left"
    )
    needed["net"] = needed["net"].fillna(0.0)
    keys = ["result_key", "score_type_id"]

    needed["points"] = needed["coefficient"] * needed["net"].clip(lower=0)
    points = needed.groupby(keys)["points"].sum().rename("points")

    grouped = needed.dropna(subset=["minimum_net_group"])
    group_nets = grouped.groupby([*keys, "minimum_net_group"])["net"].sum().rename("group_net").reset_index()
    group_nets["minimum_net"] = group_nets["score_type_id"].map(
        {score_type_id: rule.minimum_net for score_type_id, rule in rules.items()}
    )
    met = (
        (group_nets["group_net"] >= group_nets["minimum_net"])
        .groupby([group_nets[key] for key in keys])
        .any()
        .rename("minimum_met")
    )

    scores = pairs.join(points, on=keys).join(met, on=keys)
    # A score type without minimum-net groups has no such rule.
    minimum_met = scores["minimum_met"].astype("boolean").fillna(True).astype(bool)
    constant = scores["score_type_id"].map({key: rule.constant for key, rule in rules.items()})
    maximum = scores["score_type_id"].map({key: rule.max_score for key, rule in rules.items()})

    reason = pd.Series(None, index=scores.index, dtype=object)
    reason[~minimum_met] = REASON_MINIMUM_NET_NOT_MET
    scores["not_computed_reason"] = reason
    scores["estimated_score"] = np.minimum(constant + scores["points"], maximum).where(reason.isna())
    return scores[columns]
