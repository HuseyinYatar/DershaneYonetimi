"""Coefficient scores with the real catalog: TYT alone, AYT linked to the latest published TYT."""

import json
import uuid
from datetime import date
from decimal import Decimal

import pytest
from sqlalchemy import select

from app.auth.keycloak import CurrentUser
from app.cli import DEFAULT_CATALOG, seed_catalog
from app.models import (
    AnswerKeyItem,
    ExamType,
    MockExam,
    ScoreType,
    StudentResult,
    StudentScore,
    Subject,
    SubjectResult,
    Topic,
)
from app.models.enums import ExamStatus, Role, ScoringSource
from app.services import report_service, scoring_service

pytestmark = pytest.mark.integration

BRANCH = uuid.uuid4()
AYSE, MEHMET, CAN = uuid.uuid4(), uuid.uuid4(), uuid.uuid4()
TYT_NETS_AYSE = {"TUR": 19.25, "SOS": 13.75, "MAT": 20, "FEN": 8.75}
# A SAY student: the two social tests were not taken, so the import keeps no results for them.
AYT_NETS = {"MAT": 20, "FIZ": 7, "KIM": 6, "BIY": 5}


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
    # EA: AYT Matematik reaches 0,5 net, the untaken TDE-SB1 counts as 0 net; SÖZ: none of its tests taken.
    ea_expected = (
        126.15
        + 1.20 * 19.25 + 1.38 * 13.75 + 1.43 * 20 + 1.13 * 8.75
        + 2.93 * 20
    )  # fmt: skip
    assert float(result[(AYSE, "EA")].estimated_score) == pytest.approx(ea_expected, abs=0.005)
    assert result[(AYSE, "SOZ")].not_computed_reason == "MINIMUM_NET_NOT_MET"
    # Mehmet has no TYT score (neither Türkçe nor Temel Matematik), so no AYT score either.
    assert result[(MEHMET, "SAY")].not_computed_reason == "LINKED_MINIMUM_NOT_MET"
    assert result[(MEHMET, "SAY")].estimated_score is None
    # Can has no published TYT at all.
    assert result[(CAN, "SAY")].not_computed_reason == "LINKED_RESULT_MISSING"
    assert result[(CAN, "SAY")].linked_mock_exam_id is None


def test_result_detail_shows_where_an_ayt_score_comes_from(db_session, seeded) -> None:
    """A SAY student's EA counts the untaken TDE-SB1 as 0: its subjects are listed as not taken, and
    the score parts (constant, TYT, each AYT subject) add up to the score."""
    tyt_subjects, ayt_subjects = seeded
    tyt = add_exam(db_session, "TYT", date(2026, 11, 1), ExamStatus.PUBLISHED)
    add_result(db_session, tyt, AYSE, tyt_subjects, TYT_NETS_AYSE)
    ayt = add_exam(db_session, "AYT", date(2026, 11, 8), ExamStatus.PUBLISHED)
    add_result(db_session, ayt, AYSE, ayt_subjects, AYT_NETS)
    topic_id = db_session.scalar(select(Topic.id).where(Topic.subject_id == ayt_subjects["EDB"]))
    db_session.add_all(
        AnswerKeyItem(
            mock_exam_id=ayt.id,
            subject_id=ayt_subjects["EDB"],
            topic_id=topic_id,
            booklet="A",
            question_no=number,
            correct_answer="A",
        )
        for number in range(1, 25)
    )
    db_session.flush()
    scoring_service.recalculate_exam(db_session, ayt, refresh_institutional=True)
    db_session.commit()
    student = CurrentUser(id=AYSE, roles=frozenset({Role.STUDENT}), branch_id=BRANCH)

    detail = report_service.student_result_detail(db_session, student, AYSE, ayt.id)

    subjects = {subject.subject_name: subject for subject in detail.subjects}
    assert {name for name, s in subjects.items() if not s.taken} == {
        "Türk Dili ve Edebiyatı",
        "Tarih-1",
        "Coğrafya-1",
    }  # EA's TDE-SB1; SÖZ-only subjects are not listed because SÖZ is not computed
    assert subjects["Türk Dili ve Edebiyatı"].question_count == 24
    assert subjects["Matematik"].taken
    ea = next(score for score in detail.scores if score.score_type_code == "EA")
    labels = [part.label for part in ea.breakdown]
    assert labels[:2] == ["Sabit puan", f"TYT netleri ({tyt.name})"]
    assert float(sum(part.points for part in ea.breakdown)) == pytest.approx(
        float(ea.estimated_score), abs=0.02
    )
    assert next(part for part in ea.breakdown if part.label == "Matematik").points == Decimal("58.60")
    assert next(part for part in ea.breakdown if part.label == "Türk Dili ve Edebiyatı").points == 0


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
