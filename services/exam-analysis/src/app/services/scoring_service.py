"""Recomputes statistics, estimated scores and ranks of one exam from its stored subject results.

Called after every change to an exam's results (import, late matching, key change) and after
national statistics are entered. The caller holds the mock_exam row lock.
"""

import uuid

import pandas as pd
import structlog
from sqlalchemy import delete, insert, select, update
from sqlalchemy.orm import Session

from app.models import ExamSubjectStatistic, MockExam, StudentResult, StudentScore
from app.models.enums import ScoringSource
from app.observability.metrics import SCORE_CALCULATION_SKIPPED
from app.repositories import catalog as catalog_repo
from app.repositories import results as results_repo
from app.repositories.frames import read_frame, to_records
from app.scoring.coefficient_score import CoefficientRule, compute_coefficient_scores
from app.scoring.ranking import rank_descending, rank_descending_within
from app.scoring.standard_score import REASON_STATISTIC_MISSING, compute_scores, compute_statistics

logger = structlog.get_logger(__name__)

REASON_LINKED_RESULT_MISSING = "LINKED_RESULT_MISSING"
REASON_LINKED_MINIMUM_NOT_MET = "LINKED_MINIMUM_NOT_MET"


def recalculate_exam(session: Session, exam: MockExam, *, refresh_institutional: bool) -> None:
    """Recomputes statistics, estimated scores and ranks of one exam.

    refresh_institutional=True first recomputes the branch's own mean and standard deviation (results
    changed); False keeps them (only national statistics changed). Score types that cannot be
    computed are logged and counted, not raised.
    """
    subject_nets = results_repo.subject_nets_frame(session, exam.id)
    if subject_nets.empty:
        logger.info("scoring.recalculation_skipped", reason="no_results", mock_exam_id=str(exam.id))
        return

    if refresh_institutional:
        _store_institutional_statistics(session, exam.id, subject_nets)
    session.flush()

    catalog = catalog_repo.scoring_catalog(session, exam.exam_type_id)
    if catalog.weights.empty:
        SCORE_CALCULATION_SKIPPED.labels(reason="no_score_types").inc()
        logger.warning(
            "scoring.no_score_types",
            mock_exam_id=str(exam.id),
            exam_type_id=str(exam.exam_type_id),
            reason="exam type has no weighted score types; only nets and ranks are computed",
        )

    coefficient_ids = {st.id for st in catalog.score_types if st.coefficient_year is not None}
    uses_coefficients = catalog.weights["score_type_id"].isin(coefficient_ids)
    scores = pd.concat(
        [
            _standard_scores(session, exam, subject_nets, catalog, catalog.weights[~uses_coefficients]),
            _coefficient_scores(session, exam, subject_nets, catalog, catalog.weights[uses_coefficients]),
        ],
        ignore_index=True,
    )
    _replace_scores(session, exam, scores)
    _update_net_ranks(session, exam.id)
    logger.info(
        "scoring.recalculated",
        mock_exam_id=str(exam.id),
        results=int(subject_nets["result_key"].nunique()),
        score_types=len(catalog.score_types),
        coefficient_score_types=len(coefficient_ids),
        scoring_source=exam.scoring_source.value,
    )


def _standard_scores(
    session: Session,
    exam: MockExam,
    subject_nets: pd.DataFrame,
    catalog: catalog_repo.ScoringCatalog,
    weights: pd.DataFrame,
) -> pd.DataFrame:
    """Standard-score model (score types without coefficients, e.g. LGS): weighted standard scores of
    the exam's statistics source plus base_score."""
    if weights.empty:
        return _empty_scores()
    statistics = _statistics_frame(session, exam.id, exam.scoring_source)
    base_scores = {
        st.id: (None if st.base_score is None else float(st.base_score))
        for st in catalog.score_types
        if st.coefficient_year is None
    }
    computation = compute_scores(
        subject_nets, statistics, weights[["score_type_id", "subject_id", "weight"]], base_scores
    )
    for skipped in computation.skipped:
        SCORE_CALCULATION_SKIPPED.labels(reason=skipped.reason).inc()
        # Missing national statistics is an expected state until an admin enters them.
        expected = (
            skipped.reason == REASON_STATISTIC_MISSING and exam.scoring_source == ScoringSource.NATIONAL
        )
        log = logger.info if expected else logger.warning
        log(
            "scoring.score_type_skipped",
            mock_exam_id=str(exam.id),
            score_type=catalog.codes.get(skipped.score_type_id),
            reason=skipped.reason,
            scoring_source=exam.scoring_source.value,
            subject_ids=[str(subject_id) for subject_id in skipped.subject_ids],
        )
    # bu kısım değişebilir öğrenci EA ise fen çözmez o zaman gerçi o puanı hesaplanmaz demiş
    if computation.incomplete_results:
        SCORE_CALCULATION_SKIPPED.labels(reason="incomplete_subjects").inc(computation.incomplete_results)
        logger.warning(
            "scoring.incomplete_results",
            mock_exam_id=str(exam.id),
            results=computation.incomplete_results,
            reason="results lack a subject of a score type",
        )
    scores = computation.scores
    return scores.assign(
        source=pd.Series(exam.scoring_source.value, index=scores.index, dtype=object).where(
            scores["estimated_score"].notna(), None
        ),
        not_computed_reason=None,
        linked_mock_exam_id=None,
    )


def _coefficient_scores(
    session: Session,
    exam: MockExam,
    subject_nets: pd.DataFrame,
    catalog: catalog_repo.ScoringCatalog,
    weights: pd.DataFrame,
) -> pd.DataFrame:
    """Coefficient model (app.scoring.coefficient_score). Score types that also count the tests of
    another exam type (SAY/EA/SÖZ/DİL count TYT) take those nets from the student's latest published
    exam of that type in the branch, dated on or before this exam; that exam's own score types must
    be computable too (ÖSYM: no TYT score, no AYT/YDT score)."""
    if weights.empty:
        return _empty_scores()
    rules = {
        st.id: CoefficientRule(
            constant=float(st.base_score or 0),
            max_score=float(st.max_score) if st.max_score is not None else float("inf"),
            minimum_net=float(st.minimum_net or 0),
        )
        for st in catalog.score_types
        if st.coefficient_year is not None
    }
    nets = subject_nets
    linked = pd.DataFrame(columns=["result_key", "linked_mock_exam_id", "linked_minimum_met"])
    foreign_types = set(weights["subject_exam_type_id"]) - {exam.exam_type_id}
    if len(foreign_types) > 1:
        logger.error(
            "scoring.too_many_linked_exam_types",
            mock_exam_id=str(exam.id),
            exam_type_ids=sorted(str(exam_type_id) for exam_type_id in foreign_types),
            reason="a score type may count the tests of one other exam type only",
        )
    for foreign_exam_type_id in sorted(foreign_types)[:1]:
        linked_nets, linked = _linked_nets(session, exam, foreign_exam_type_id)
        nets = pd.concat([nets, linked_nets], ignore_index=True)

    scores = compute_coefficient_scores(
        nets,
        weights[["score_type_id", "subject_id", "weight", "minimum_net_group"]].rename(
            columns={"weight": "coefficient"}
        ),
        rules,
    )
    linking = set(weights.loc[weights["subject_exam_type_id"] != exam.exam_type_id, "score_type_id"])
    scores = scores.merge(linked, on="result_key", how="left")
    is_linking = scores["score_type_id"].isin(linking)
    no_linked = is_linking & scores["linked_mock_exam_id"].isna()
    linked_failed = is_linking & ~no_linked & ~scores["linked_minimum_met"].astype("boolean").fillna(False)
    scores.loc[linked_failed, "not_computed_reason"] = REASON_LINKED_MINIMUM_NOT_MET
    scores.loc[no_linked, "not_computed_reason"] = REASON_LINKED_RESULT_MISSING
    scores.loc[no_linked | linked_failed, "estimated_score"] = float("nan")
    scores["linked_mock_exam_id"] = scores["linked_mock_exam_id"].where(is_linking, None)

    reasons = scores["not_computed_reason"].dropna().value_counts()
    for reason, count in reasons.items():
        SCORE_CALCULATION_SKIPPED.labels(reason=str(reason).lower()).inc(int(count))
    if not reasons.empty:
        # Expected (a student who left a test empty, or without a TYT yet); one summary line per exam.
        logger.info(
            "scoring.scores_not_computed",
            mock_exam_id=str(exam.id),
            reasons={str(reason): int(count) for reason, count in reasons.items()},
        )
    return scores.assign(source=None)[
        [
            "result_key",
            "score_type_id",
            "estimated_score",
            "source",
            "not_computed_reason",
            "linked_mock_exam_id",
        ]
    ]


def _linked_nets(
    session: Session, exam: MockExam, foreign_exam_type_id: uuid.UUID
) -> tuple[pd.DataFrame, pd.DataFrame]:
    """Nets of the linked exam re-keyed to this exam's results, and per result: linked_mock_exam_id
    and whether the linked exam's own score types are computable (linked_minimum_met)."""
    students = results_repo.result_students_frame(session, exam.id)
    latest = results_repo.latest_published_results_frame(
        session, exam, foreign_exam_type_id, set(students["student_id"])
    )
    mapping = students.merge(latest, on="student_id")
    linked_nets = (
        results_repo.subject_nets_of_results(session, set(mapping["linked_result_id"]))
        .rename(columns={"result_key": "linked_result_id"})
        .merge(mapping[["result_key", "linked_result_id"]], on="linked_result_id")
    )[["result_key", "subject_id", "net"]]

    foreign = catalog_repo.scoring_catalog(session, foreign_exam_type_id)
    foreign_ids = {st.id for st in foreign.score_types if st.coefficient_year is not None}
    foreign_weights = foreign.weights[foreign.weights["score_type_id"].isin(foreign_ids)]
    foreign_scores = compute_coefficient_scores(
        linked_nets,
        foreign_weights[["score_type_id", "subject_id", "weight", "minimum_net_group"]].rename(
            columns={"weight": "coefficient"}
        ),
        {
            st.id: CoefficientRule(float(st.base_score or 0), float("inf"), float(st.minimum_net or 0))
            for st in foreign.score_types
            if st.id in foreign_ids
        },
    )
    minimum_met = foreign_scores.groupby("result_key")["estimated_score"].apply(
        lambda s: bool(s.notna().all())
    )
    info = mapping[["result_key", "linked_mock_exam_id"]].assign(
        linked_minimum_met=mapping["result_key"].map(minimum_met).astype("boolean").fillna(True).astype(bool)
    )
    return linked_nets, info


def _empty_scores() -> pd.DataFrame:
    return pd.DataFrame(
        columns=[
            "result_key",
            "score_type_id",
            "estimated_score",
            "source",
            "not_computed_reason",
            "linked_mock_exam_id",
        ]
    )


# ogrenci eklendi vs gibi
def _store_institutional_statistics(
    session: Session, mock_exam_id: uuid.UUID, subject_nets: pd.DataFrame
) -> None:
    """Replaces the INSTITUTIONAL mean and standard deviation of each subject from the stored nets."""
    statistics = compute_statistics(subject_nets)
    session.execute(
        delete(ExamSubjectStatistic).where(
            ExamSubjectStatistic.mock_exam_id == mock_exam_id,
            ExamSubjectStatistic.source == ScoringSource.INSTITUTIONAL,
        )
    )
    rows = statistics.assign(
        id=[uuid.uuid4() for _ in range(len(statistics))],
        mock_exam_id=mock_exam_id,
        source=ScoringSource.INSTITUTIONAL.value,
        mean=statistics["mean"].round(3),
        std_dev=statistics["std_dev"].round(3),
    )
    session.execute(
        insert(ExamSubjectStatistic),
        to_records(
            rows[["id", "mock_exam_id", "subject_id", "source", "mean", "std_dev", "participant_count"]]
        ),
    )


def _statistics_frame(session: Session, mock_exam_id: uuid.UUID, source: ScoringSource) -> pd.DataFrame:
    """Reads the subject statistics of one source (the one the exam is scored with)."""
    frame = read_frame(
        session,
        select(
            ExamSubjectStatistic.subject_id, ExamSubjectStatistic.mean, ExamSubjectStatistic.std_dev
        ).where(ExamSubjectStatistic.mock_exam_id == mock_exam_id, ExamSubjectStatistic.source == source),
        ["subject_id", "mean", "std_dev"],
    )
    return frame.astype({"mean": float, "std_dev": float})


def _replace_scores(session: Session, exam: MockExam, scores: pd.DataFrame) -> None:
    """Deletes the exam's student scores and writes the new ones, ranked within each score type.

    A score that cannot be computed is stored empty (NULL) with its reason (coefficient model) or
    without one (standard-score model: statistics missing).
    """
    session.execute(
        delete(StudentScore)
        .where(StudentScore.student_result_id.in_(results_repo.result_ids_statement(exam.id)))
        .execution_options(synchronize_session=False)
    )
    if scores.empty:
        logger.info("scoring.no_scores_written", mock_exam_id=str(exam.id), reason="no score types")
        return
    scores = scores.assign(estimated_score=pd.to_numeric(scores["estimated_score"]))
    rows = scores.assign(
        id=[uuid.uuid4() for _ in range(len(scores))],
        student_result_id=scores["result_key"],
        estimated_score=scores["estimated_score"].round(3),
        branch_rank=rank_descending_within(scores, "estimated_score", "score_type_id"),
    )
    columns = [
        "id", "student_result_id", "score_type_id", "estimated_score", "source", "branch_rank",
        "not_computed_reason", "linked_mock_exam_id",
    ]  # fmt: skip
    session.execute(insert(StudentScore), to_records(rows[columns]))


def _update_net_ranks(session: Session, mock_exam_id: uuid.UUID) -> None:
    """Ranks all students of the exam by total net and stores branch_rank."""
    totals = results_repo.totals_frame(session, mock_exam_id)
    totals["branch_rank"] = rank_descending(totals["net"])
    session.execute(update(StudentResult), to_records(totals[["id", "branch_rank"]]))
