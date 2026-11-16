"""Coefficient model: score = constant + sum(coefficient x max(net, 0)), capped, minimum-net rule."""

import math
import uuid

import pandas as pd
import pytest

from app.scoring.coefficient_score import (
    REASON_MINIMUM_NET_NOT_MET,
    REASON_SUBJECT_MISSING,
    CoefficientRule,
    compute_coefficient_scores,
)

TUR, SOS, MAT, FEN = (uuid.uuid4() for _ in range(4))
FIZ, KIM, BIY = (uuid.uuid4() for _ in range(3))
TYT, SAY = uuid.uuid4(), uuid.uuid4()

# 2026 TYT estimate (seed/catalog.json): minimum-net groups Türkçe (1) and Temel Matematik (2).
TYT_COEFFICIENTS = pd.DataFrame(
    [(TYT, TUR, 2.71, 1), (TYT, SOS, 3.14, None), (TYT, MAT, 3.25, 2), (TYT, FEN, 2.57, None)],
    columns=["score_type_id", "subject_id", "coefficient", "minimum_net_group"],
)
TYT_RULE = {TYT: CoefficientRule(constant=147.50, max_score=500, minimum_net=0.5)}


def nets(rows: list[tuple[str, uuid.UUID, float]]) -> pd.DataFrame:
    return pd.DataFrame(rows, columns=["result_key", "subject_id", "net"])


def tyt_nets(key: str, tur: float, sos: float, mat: float, fen: float) -> list[tuple[str, uuid.UUID, float]]:
    return [(key, TUR, tur), (key, SOS, sos), (key, MAT, mat), (key, FEN, fen)]


def scores_of(frame: pd.DataFrame) -> dict[str, tuple[float, object]]:
    return {
        row.result_key: (row.estimated_score, row.not_computed_reason)
        for row in frame.itertuples(index=False)
    }


def test_reproduces_the_public_calculator() -> None:
    """19,25 / 13,75 / 20 / 8,75 net: the calculator shows 330,26 with unrounded coefficients."""
    result = compute_coefficient_scores(
        nets(tyt_nets("ayse", 19.25, 13.75, 20, 8.75)), TYT_COEFFICIENTS, TYT_RULE
    )

    score, reason = scores_of(result)["ayse"]
    assert score == pytest.approx(147.50 + 2.71 * 19.25 + 3.14 * 13.75 + 3.25 * 20 + 2.57 * 8.75)
    assert score == pytest.approx(330.26, abs=0.1)
    assert reason is None or pd.isna(reason)


def test_all_correct_is_capped_at_500_and_negative_nets_count_as_zero() -> None:
    frame = nets(
        tyt_nets("full", 40, 20, 40, 20)  # 500,1 before the cap
        + tyt_nets("negative", 10, -2, 1, -1.25)
    )

    result = scores_of(compute_coefficient_scores(frame, TYT_COEFFICIENTS, TYT_RULE))

    assert result["full"][0] == 500
    assert result["negative"][0] == pytest.approx(147.50 + 2.71 * 10 + 3.25 * 1)


def test_minimum_net_rule_needs_turkish_or_basic_math() -> None:
    frame = nets(
        tyt_nets("only_turkish", 0.5, 0, 0, 0)
        + tyt_nets("only_math", 0, 0, 0.5, 0)
        + tyt_nets("neither", 0.25, 15, -0.5, 10)  # other tests do not count for the rule
    )

    result = scores_of(compute_coefficient_scores(frame, TYT_COEFFICIENTS, TYT_RULE))

    assert result["only_turkish"][0] == pytest.approx(147.50 + 2.71 * 0.5)
    assert result["only_math"][0] == pytest.approx(147.50 + 3.25 * 0.5)
    assert math.isnan(result["neither"][0])
    assert result["neither"][1] == REASON_MINIMUM_NET_NOT_MET


def test_a_group_of_subjects_is_one_test_for_the_minimum_rule() -> None:
    """SAY: AYT Matematik or the Fen Bilimleri test (Fizik + Kimya + Biyoloji, summed)."""
    coefficients = pd.DataFrame(
        [(SAY, MAT, 2.97, 1), (SAY, FIZ, 2.47, 2), (SAY, KIM, 2.53, 2), (SAY, BIY, 2.60, 2)],
        columns=["score_type_id", "subject_id", "coefficient", "minimum_net_group"],
    )
    rule = {SAY: CoefficientRule(constant=125.55, max_score=500, minimum_net=0.5)}
    frame = nets(
        [
            *[("science", MAT, 0), ("science", FIZ, 0.25), ("science", KIM, 0.25), ("science", BIY, 0)],
            *[("none", MAT, 0.25), ("none", FIZ, 0.25), ("none", KIM, 0), ("none", BIY, -0.25)],
        ]
    )

    result = scores_of(compute_coefficient_scores(frame, coefficients, rule))

    assert result["science"][0] == pytest.approx(125.55 + 2.47 * 0.25 + 2.53 * 0.25)
    assert result["none"][1] == REASON_MINIMUM_NET_NOT_MET


def test_a_missing_subject_is_not_computed() -> None:
    frame = nets([("partial", TUR, 30), ("partial", MAT, 30)])

    result = scores_of(compute_coefficient_scores(frame, TYT_COEFFICIENTS, TYT_RULE))

    assert math.isnan(result["partial"][0])
    assert result["partial"][1] == REASON_SUBJECT_MISSING


def test_a_score_type_without_minimum_groups_has_no_net_rule() -> None:
    coefficients = TYT_COEFFICIENTS.assign(minimum_net_group=None)

    result = scores_of(compute_coefficient_scores(nets(tyt_nets("low", 0, 0, 0, 0)), coefficients, TYT_RULE))

    assert result["low"][0] == pytest.approx(147.50)
