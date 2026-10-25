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
from app.scoring.ranking import rank_descending, rank_descending_within
from app.scoring.standard_score import REASON_STATISTIC_MISSING, compute_scores, compute_statistics

logger = structlog.get_logger(__name__)


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

    statistics = _statistics_frame(session, exam.id, exam.scoring_source)
    weights, base_scores, codes = catalog_repo.score_type_weights(session, exam.exam_type_id)
    if weights.empty:
        SCORE_CALCULATION_SKIPPED.labels(reason="no_score_types").inc()
        logger.warning(
            "scoring.no_score_types",
            mock_exam_id=str(exam.id),
            exam_type_id=str(exam.exam_type_id),
            reason="exam type has no weighted score types; only nets and ranks are computed",
        )

    computation = compute_scores(subject_nets, statistics, weights, base_scores)
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
            score_type=codes.get(skipped.score_type_id),
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

    _replace_scores(session, exam, computation.scores)
    _update_net_ranks(session, exam.id)
    logger.info(
        "scoring.recalculated",
        mock_exam_id=str(exam.id),
        results=int(subject_nets["result_key"].nunique()),
        score_types=len(codes),
        skipped_score_types=len(computation.skipped),
        scoring_source=exam.scoring_source.value,
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

    A score without statistics is stored empty (NULL).
    """
    session.execute(
        delete(StudentScore)
        .where(StudentScore.student_result_id.in_(results_repo.result_ids_statement(exam.id)))
        .execution_options(synchronize_session=False)
    )
    if scores.empty:
        logger.info("scoring.no_scores_written", mock_exam_id=str(exam.id), reason="no score types")
        return
    has_score = scores["estimated_score"].notna()
    rows = scores.assign(
        id=[uuid.uuid4() for _ in range(len(scores))],
        student_result_id=scores["result_key"],
        estimated_score=scores["estimated_score"].round(3),
        source=pd.Series(exam.scoring_source.value, index=scores.index, dtype=object).where(has_score, None),
        branch_rank=rank_descending_within(scores, "estimated_score", "score_type_id"),
    )
    session.execute(
        insert(StudentScore),
        to_records(
            rows[["id", "student_result_id", "score_type_id", "estimated_score", "source", "branch_rank"]]
        ),
    )


def _update_net_ranks(session: Session, mock_exam_id: uuid.UUID) -> None:
    """Ranks all students of the exam by total net and stores branch_rank."""
    totals = results_repo.totals_frame(session, mock_exam_id)
    totals["branch_rank"] = rank_descending(totals["net"])
    session.execute(update(StudentResult), to_records(totals[["id", "branch_rank"]]))
