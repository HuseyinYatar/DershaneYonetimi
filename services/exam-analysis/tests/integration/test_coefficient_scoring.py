"""Coefficient scores with the real catalog: TYT alone, AYT linked to the latest published TYT."""

import json
import uuid
from datetime import date
from decimal import Decimal

import pytest
from sqlalchemy import select

from app.cli import DEFAULT_CATALOG, seed_catalog
from app.models import ExamType, MockExam, ScoreType, StudentResult, StudentScore, Subject, SubjectResult
from app.models.enums import ExamStatus, ScoringSource
from app.services import scoring_service

pytestmark = pytest.mark.integration

BRANCH = uuid.uuid4()
AYSE, MEHMET, CAN = uuid.uuid4(), uuid.uuid4(), uuid.uuid4()
TYT_NETS_AYSE = {"TUR": 19.25, "SOS": 13.75, "MAT": 20, "FEN": 8.75}
AYT_NETS = {"MAT": 20, "FIZ": 7, "KIM": 6, "BIY": 5, "EDB": 0, "TAR1": 0, "COG1": 0, "TAR2": 0, "COG2": 0}
AYT_NETS |= {"FEL": 0, "DIN": 0}


def subjects_of(session, exam_type_code: str) -> dict[str, uuid.UUID]:
    rows = session.execute(
        select(Subject.code, Subject.id)
        .join(ExamType, ExamType.id == Subject.exam_type_id)
        .where(ExamType.code == exam_type_code)
    ).all()
    return dict(rows)


def add_exam(session, exam_type_code: str, exam_date: date, status: ExamStatus) -> MockExam:
    exam_type_id = session.scalar(select(ExamType.id).where(ExamType.code == exam_type_code))
    exam = MockExam(
        exam_type_id=exam_type_id,
        name=f"{exam_type_code} {exam_date.isoformat()}",
        exam_date=exam_date,
        branch_id=BRANCH,
        status=status,
        scoring_source=ScoringSource.INSTITUTIONAL,
        created_by=uuid.uuid4(),
    )
    session.add(exam)
    session.flush()
    return exam


def add_result(session, exam: MockExam, student_id: uuid.UUID, subject_ids, nets: dict[str, float]) -> None:
    result = StudentResult(
        mock_exam_id=exam.id,
        student_id=student_id,
        booklet="A",
        correct=0,
        wrong=0,
        blank=0,
        net=Decimal(str(sum(nets.values()))),
    )
    session.add(result)
    session.flush()
    session.add_all(
        SubjectResult(
            student_result_id=result.id,
            subject_id=subject_ids[code],
            correct=0,
            wrong=0,
            blank=0,
            net=Decimal(str(net)),
        )
        for code, net in nets.items()
    )
    session.flush()


def scores(session, exam: MockExam) -> dict[tuple[uuid.UUID, str], StudentScore]:
    rows = session.execute(
        select(StudentScore, StudentResult.student_id, ScoreType.code)
        .join(StudentResult, StudentResult.id == StudentScore.student_result_id)
        .join(ScoreType, ScoreType.id == StudentScore.score_type_id)
        .where(StudentResult.mock_exam_id == exam.id)
    ).all()
    return {(student_id, code): score for score, student_id, code in rows}


@pytest.fixture
def seeded(db_session):
    seed_catalog(db_session, json.loads(DEFAULT_CATALOG.read_text(encoding="utf-8")))
    return subjects_of(db_session, "TYT"), subjects_of(db_session, "AYT")


def test_tyt_score_and_minimum_net_rule(db_session, seeded) -> None:
    tyt_subjects, _ = seeded
    exam = add_exam(db_session, "TYT", date(2026, 11, 1), ExamStatus.PROCESSED)
    add_result(db_session, exam, AYSE, tyt_subjects, TYT_NETS_AYSE)
    add_result(db_session, exam, MEHMET, tyt_subjects, {"TUR": 0.25, "SOS": 15, "MAT": -0.5, "FEN": 10})

    scoring_service.recalculate_exam(db_session, exam, refresh_institutional=True)

    result = scores(db_session, exam)
    ayse = result[(AYSE, "TYT")]
    assert float(ayse.estimated_score) == pytest.approx(330.33, abs=0.005)
    assert (ayse.source, ayse.not_computed_reason, ayse.linked_mock_exam_id, ayse.branch_rank) == (
        None,
        None,
        None,
        1,
    )
    mehmet = result[(MEHMET, "TYT")]
    assert (mehmet.estimated_score, mehmet.not_computed_reason, mehmet.branch_rank) == (
        None,
        "MINIMUM_NET_NOT_MET",
        None,
    )


def test_ayt_counts_the_latest_published_tyt_on_or_before_its_date(db_session, seeded) -> None:
    tyt_subjects, ayt_subjects = seeded
    linked_tyt = add_exam(db_session, "TYT", date(2026, 11, 1), ExamStatus.PUBLISHED)
    add_result(db_session, linked_tyt, AYSE, tyt_subjects, TYT_NETS_AYSE)
    add_result(db_session, linked_tyt, MEHMET, tyt_subjects, {"TUR": 0, "SOS": 10, "MAT": 0, "FEN": 10})
    older_tyt = add_exam(db_session, "TYT", date(2026, 10, 1), ExamStatus.PUBLISHED)
    add_result(db_session, older_tyt, AYSE, tyt_subjects, {"TUR": 1, "SOS": 1, "MAT": 1, "FEN": 1})
    draft_tyt = add_exam(db_session, "TYT", date(2026, 11, 5), ExamStatus.PROCESSED)  # not published
    add_result(db_session, draft_tyt, AYSE, tyt_subjects, {"TUR": 40, "SOS": 20, "MAT": 40, "FEN": 20})
    later_tyt = add_exam(db_session, "TYT", date(2026, 11, 20), ExamStatus.PUBLISHED)  # after the AYT
    add_result(db_session, later_tyt, AYSE, tyt_subjects, {"TUR": 40, "SOS": 20, "MAT": 40, "FEN": 20})

    ayt = add_exam(db_session, "AYT", date(2026, 11, 8), ExamStatus.PROCESSED)
    for student in (AYSE, MEHMET, CAN):
        add_result(db_session, ayt, student, ayt_subjects, AYT_NETS)

    scoring_service.recalculate_exam(db_session, ayt, refresh_institutional=True)

    result = scores(db_session, ayt)
    say = result[(AYSE, "SAY")]
    expected = (
        125.55
        + 1.21 * 19.25 + 1.40 * 13.75 + 1.45 * 20 + 1.15 * 8.75
        + 2.97 * 20 + 2.47 * 7 + 2.53 * 6 + 2.60 * 5
    )  # fmt: skip
    assert float(say.estimated_score) == pytest.approx(expected, abs=0.005)
    assert say.linked_mock_exam_id == linked_tyt.id
    # EA: AYT Matematik reaches 0,5 net; SÖZ: both of its tests are empty.
    assert result[(AYSE, "EA")].estimated_score is not None
    assert result[(AYSE, "SOZ")].not_computed_reason == "MINIMUM_NET_NOT_MET"
    # Mehmet has no TYT score (neither Türkçe nor Temel Matematik), so no AYT score either.
    assert result[(MEHMET, "SAY")].not_computed_reason == "LINKED_MINIMUM_NOT_MET"
    assert result[(MEHMET, "SAY")].estimated_score is None
    # Can has no published TYT at all.
    assert result[(CAN, "SAY")].not_computed_reason == "LINKED_RESULT_MISSING"
    assert result[(CAN, "SAY")].linked_mock_exam_id is None


def test_lgs_keeps_the_standard_score_model(db_session, seeded) -> None:
    lgs_subjects = subjects_of(db_session, "LGS")
    exam = add_exam(db_session, "LGS", date(2026, 11, 1), ExamStatus.PROCESSED)
    nets = {"TUR": 10, "MAT": 10, "FEN": 10, "INK": 5, "DIN": 5, "ING": 5}
    add_result(db_session, exam, AYSE, lgs_subjects, nets)
    add_result(db_session, exam, MEHMET, lgs_subjects, {code: net / 2 for code, net in nets.items()})

    scoring_service.recalculate_exam(db_session, exam, refresh_institutional=True)

    result = scores(db_session, exam)
    assert result[(AYSE, "LGS")].source == ScoringSource.INSTITUTIONAL
    assert result[(AYSE, "LGS")].estimated_score > result[(MEHMET, "LGS")].estimated_score
