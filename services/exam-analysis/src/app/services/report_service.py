"""Read models for teachers, admins and students. Students only ever see published exams."""

import uuid
from collections import defaultdict
from decimal import Decimal

from sqlalchemy import func, select, true
from sqlalchemy.orm import Session, aliased

from app.auth.access import ensure_can_view_student
from app.auth.keycloak import CurrentUser
from app.cache import ResultCache, exam_analysis_key, progress_key
from app.errors import NotFoundError
from app.models import (
    AnswerKeyItem,
    MockExam,
    ScoreType,
    ScoreTypeWeight,
    StudentResult,
    StudentScore,
    Subject,
    SubjectResult,
    Topic,
    TopicResult,
)
from app.models.enums import ExamStatus
from app.repositories import catalog as catalog_repo
from app.repositories import replicas as replicas_repo
from app.schemas.results import (
    ExamResultRow,
    ProgressPoint,
    ProgressSubjectNet,
    ScorePart,
    ScoreRead,
    StudentExamSummary,
    StudentProgress,
    StudentResultDetail,
    SubjectResultRead,
    TopicAnalysisItem,
    TopicResultRead,
)
from app.services import mock_exam_service


def _scores_by_result(session: Session, result_ids: list[uuid.UUID]) -> dict[uuid.UUID, list[ScoreRead]]:
    """Loads the estimated scores of the given results, grouped by result id."""
    linked_exam = aliased(MockExam)
    rows = session.execute(
        select(StudentScore, ScoreType.code, ScoreType.coefficient_year, linked_exam.name)
        .join(ScoreType, ScoreType.id == StudentScore.score_type_id)
        .outerjoin(linked_exam, linked_exam.id == StudentScore.linked_mock_exam_id)
        .where(StudentScore.student_result_id.in_(result_ids))
        .order_by(ScoreType.code)
    ).all()
    scores: dict[uuid.UUID, list[ScoreRead]] = defaultdict(list)
    for score, code, coefficient_year, linked_exam_name in rows:
        scores[score.student_result_id].append(
            ScoreRead(
                score_type_id=score.score_type_id,
                score_type_code=code,
                estimated_score=score.estimated_score,
                source=score.source,
                branch_rank=score.branch_rank,
                coefficient_year=coefficient_year,
                not_computed_reason=score.not_computed_reason,
                linked_exam_name=linked_exam_name,
            )
        )
    return scores


def _nets_of(session: Session, result_id: uuid.UUID) -> dict[uuid.UUID, Decimal]:
    rows = session.execute(
        select(SubjectResult.subject_id, SubjectResult.net).where(SubjectResult.student_result_id == result_id)
    ).all()
    return dict(rows)


def _linked_nets(session: Session, student_id: uuid.UUID, mock_exam_id: uuid.UUID) -> dict[uuid.UUID, Decimal]:
    """The student's subject nets in the exam an AYT/YDT score counted (its TYT)."""
    rows = session.execute(
        select(SubjectResult.subject_id, SubjectResult.net)
        .join(StudentResult, StudentResult.id == SubjectResult.student_result_id)
        .where(StudentResult.mock_exam_id == mock_exam_id, StudentResult.student_id == student_id)
    ).all()
    return dict(rows)


def _with_breakdown(
    session: Session, exam: MockExam, result: StudentResult, scores: list[ScoreRead]
) -> list[ScoreRead]:
    """Adds to every computed coefficient score the points of its parts: the constant, the linked TYT
    nets together, and each own subject (an untaken test shows 0). Mirrors coefficient_score."""
    computed = {s.score_type_id: s for s in scores if s.estimated_score is not None and s.coefficient_year}
    if not computed:
        return scores
    base = dict(
        session.execute(select(ScoreType.id, ScoreType.base_score).where(ScoreType.id.in_(computed))).all()
    )
    linked_ids = dict(
        session.execute(
            select(StudentScore.score_type_id, StudentScore.linked_mock_exam_id).where(
                StudentScore.student_result_id == result.id
            )
        ).all()
    )
    weights = session.execute(
        select(ScoreTypeWeight.score_type_id, ScoreTypeWeight.subject_id, ScoreTypeWeight.weight, Subject)
        .join(Subject, Subject.id == ScoreTypeWeight.subject_id)
        .where(ScoreTypeWeight.score_type_id.in_(computed))
        .order_by(Subject.code)
    ).all()
    own = _nets_of(session, result.id)
    linked_cache: dict[uuid.UUID, dict[uuid.UUID, Decimal]] = {}

    parts: dict[uuid.UUID, list[ScorePart]] = defaultdict(list)
    linked_points: dict[uuid.UUID, Decimal] = defaultdict(Decimal)
    for score_type_id, subject_id, weight, subject in weights:
        if subject.exam_type_id != exam.exam_type_id:  # a TYT test of an AYT/YDT score type
            linked_exam_id = linked_ids.get(score_type_id)
            if linked_exam_id is None:
                continue
            if linked_exam_id not in linked_cache:
                linked_cache[linked_exam_id] = _linked_nets(session, result.student_id, linked_exam_id)
            net = linked_cache[linked_exam_id].get(subject_id, Decimal(0))
            linked_points[score_type_id] += weight * max(net, Decimal(0))
        else:
            points = weight * max(own.get(subject_id, Decimal(0)), Decimal(0))
            parts[score_type_id].append(ScorePart(label=subject.name, points=round(points, 2)))

    result_scores = []
    for score in scores:
        if score.score_type_id not in computed:
            result_scores.append(score)
            continue
        head = [ScorePart(label="Sabit puan", points=round(base.get(score.score_type_id) or Decimal(0), 2))]
        if score.score_type_id in linked_points:
            label = f"TYT netleri ({score.linked_exam_name})" if score.linked_exam_name else "TYT netleri"
            head.append(ScorePart(label=label, points=round(linked_points[score.score_type_id], 2)))
        # Subjects that gave points first; the untaken tests (0 points) at the end.
        own_parts = sorted(parts[score.score_type_id], key=lambda part: part.points == 0)
        result_scores.append(score.model_copy(update={"breakdown": head + own_parts}))
    return result_scores


def _untaken_subjects(
    session: Session, exam: MockExam, result: StudentResult, scores: list[ScoreRead], taken: set[uuid.UUID]
) -> list[tuple[str, SubjectResultRead]]:
    """Subjects of untaken optional tests that count (as 0 net) in one of the result's computed
    scores, e.g. Edebiyat in a SAY student's EA. Returned with their code for ordering."""
    computed = [s.score_type_id for s in scores if s.estimated_score is not None and s.coefficient_year]
    if not computed:
        return []
    question_count = func.count(func.distinct(AnswerKeyItem.id))
    rows = session.execute(
        select(Subject.id, Subject.code, Subject.name, question_count)
        .join(ScoreTypeWeight, ScoreTypeWeight.subject_id == Subject.id)
        .outerjoin(
            AnswerKeyItem,
            (AnswerKeyItem.subject_id == Subject.id)
            & (AnswerKeyItem.mock_exam_id == exam.id)
            & (AnswerKeyItem.booklet == result.booklet)
            & AnswerKeyItem.is_cancelled.is_(False),
        )
        .where(
            ScoreTypeWeight.score_type_id.in_(computed),
            Subject.exam_type_id == exam.exam_type_id,
            Subject.id.not_in(taken) if taken else true(),
        )
        .group_by(Subject.id, Subject.code, Subject.name)
    ).all()
    return [
        (
            code,
            SubjectResultRead(
                subject_id=subject_id,
                subject_name=name,
                correct=0,
                wrong=0,
                blank=0,
                net=Decimal(0),
                taken=False,
                question_count=int(count),
            ),
        )
        for subject_id, code, name, count in rows
    ]


def exam_results(session: Session, user: CurrentUser, mock_exam_id: uuid.UUID) -> list[ExamResultRow]:
    """Result list of one exam for teachers and admins: nets, ranks and scores of every student."""
    exam = mock_exam_service.get_for_staff(session, user, mock_exam_id)
    results = list(
        session.scalars(
            select(StudentResult)
            .where(StudentResult.mock_exam_id == exam.id)
            .order_by(StudentResult.branch_rank.nulls_last(), StudentResult.student_id)
        )
    )
    scores = _scores_by_result(session, [result.id for result in results])
    names = replicas_repo.full_names(session, [result.student_id for result in results])
    return [
        ExamResultRow(
            student_id=result.student_id,
            full_name=names.get(result.student_id),
            booklet=result.booklet,
            correct=result.correct,
            wrong=result.wrong,
            blank=result.blank,
            net=result.net,
            branch_rank=result.branch_rank,
            scores=scores.get(result.id, []),
        )
        for result in results
    ]


def exam_topic_analysis(
    session: Session, user: CurrentUser, mock_exam_id: uuid.UUID, cache: ResultCache
) -> list[TopicAnalysisItem]:
    """Success rate of every topic in one exam across all students, weakest first. Cached in Redis."""
    exam = mock_exam_service.get_for_staff(session, user, mock_exam_id)
    cached = cache.get_json(exam_analysis_key(exam.id))
    if cached is not None:
        return [TopicAnalysisItem.model_validate(item) for item in cached]

    rows = session.execute(
        select(
            Topic.id,
            Topic.name,
            Topic.subject_id,
            func.count(func.distinct(TopicResult.student_result_id)),
            func.sum(TopicResult.question_count),
            func.sum(TopicResult.correct),
        )
        .join(TopicResult, TopicResult.topic_id == Topic.id)
        .join(StudentResult, StudentResult.id == TopicResult.student_result_id)
        .where(StudentResult.mock_exam_id == exam.id)
        .group_by(Topic.id, Topic.name, Topic.subject_id)
    ).all()
    items = sorted(
        (
            TopicAnalysisItem(
                topic_id=topic_id,
                topic_name=name,
                subject_id=subject_id,
                student_count=students,
                question_count=int(questions),
                correct=int(correct),
                success_rate=float(correct) / float(questions),
            )
            for topic_id, name, subject_id, students, questions, correct in rows
        ),
        key=lambda item: (item.success_rate, item.topic_name),
    )
    cache.set_json(exam_analysis_key(exam.id), [item.model_dump(mode="json") for item in items])
    return items


def student_results(session: Session, user: CurrentUser, student_id: uuid.UUID) -> list[StudentExamSummary]:
    """Summaries of the student's published exams, newest first."""
    ensure_can_view_student(session, user, student_id)
    rows = session.execute(
        select(StudentResult, MockExam)
        .join(MockExam, MockExam.id == StudentResult.mock_exam_id)
        .where(StudentResult.student_id == student_id, MockExam.status == ExamStatus.PUBLISHED)
        .order_by(MockExam.exam_date.desc())
    ).all()
    scores = _scores_by_result(session, [result.id for result, _ in rows])
    return [_summary(result, exam, scores.get(result.id, [])) for result, exam in rows]


def _summary(result: StudentResult, exam: MockExam, scores: list[ScoreRead]) -> StudentExamSummary:
    """Builds the short summary (exam, net, rank, scores) of one result."""
    return StudentExamSummary(
        mock_exam_id=exam.id,
        exam_name=exam.name,
        exam_type_id=exam.exam_type_id,
        exam_date=exam.exam_date,
        net=result.net,
        branch_rank=result.branch_rank,
        scores=scores,
    )


def student_result_detail( session: Session, user: CurrentUser, student_id: uuid.UUID, mock_exam_id: uuid.UUID) -> StudentResultDetail:
    """Full result of one student in one published exam: totals, subjects and topics."""
    ensure_can_view_student(session, user, student_id)
    row = session.execute(
        select(StudentResult, MockExam)
        .join(MockExam, MockExam.id == StudentResult.mock_exam_id)
        .where(
            StudentResult.student_id == student_id,
            StudentResult.mock_exam_id == mock_exam_id,
            MockExam.status == ExamStatus.PUBLISHED,
        )
    ).one_or_none()
    if row is None:
        raise NotFoundError(
            "RESULT_NOT_FOUND",
            "No published result for this student and exam",
            student_id=str(student_id),
            mock_exam_id=str(mock_exam_id),
        )
    result, exam = row
    subjects = session.execute(
        select(SubjectResult, Subject.code, Subject.name)
        .join(Subject, Subject.id == SubjectResult.subject_id)
        .where(SubjectResult.student_result_id == result.id)
    ).all()
    topics = session.execute(
        select(TopicResult, Topic.name)
        .join(Topic, Topic.id == TopicResult.topic_id)
        .where(TopicResult.student_result_id == result.id)
        .order_by(TopicResult.success_rate, Topic.name)
    ).all()
    scores = _with_breakdown(session, exam, result, _scores_by_result(session, [result.id]).get(result.id, []))
    summary = _summary(result, exam, scores)
    taken = [
        (
            code,
            SubjectResultRead(
                subject_id=subject.subject_id,
                subject_name=name,
                correct=subject.correct,
                wrong=subject.wrong,
                blank=subject.blank,
                net=subject.net,
            ),
        )
        for subject, code, name in subjects
    ]
    # Untaken tests that still count in a computed score show up, so the score's sources are visible.
    untaken = _untaken_subjects(session, exam, result, scores, {subject.subject_id for subject, _, _ in subjects})
    return StudentResultDetail(
        **summary.model_dump(),
        booklet=result.booklet,
        correct=result.correct,
        wrong=result.wrong,
        blank=result.blank,
        # Taken tests first, then the untaken ones that count in a score.
        subjects=[read for _, read in sorted(taken) + sorted(untaken)],
        topics=[
            TopicResultRead(
                topic_id=topic.topic_id,
                topic_name=name,
                question_count=topic.question_count,
                correct=topic.correct,
                success_rate=topic.success_rate,
            )
            for topic, name in topics
        ],
    )


def student_progress(session: Session, user: CurrentUser, student_id: uuid.UUID, exam_type_id: uuid.UUID, cache: ResultCache) -> StudentProgress:
    """Net development over the student's published exams of one exam type.
    One point per exam with total and subject nets. Cached in Redis.
    """
    ensure_can_view_student(session, user, student_id)
    catalog_repo.get_exam_type(session, exam_type_id)
    key = progress_key(student_id, exam_type_id)
    cached = cache.get_json(key)
    if cached is not None:
        return StudentProgress.model_validate(cached)

    rows = session.execute(
        select(
            MockExam.id,
            MockExam.name,
            MockExam.exam_date,
            StudentResult.net,
            SubjectResult.subject_id,
            SubjectResult.net,
        )
        .join(StudentResult, StudentResult.mock_exam_id == MockExam.id)
        .join(SubjectResult, SubjectResult.student_result_id == StudentResult.id)
        .where(
            StudentResult.student_id == student_id,
            MockExam.exam_type_id == exam_type_id,
            MockExam.status == ExamStatus.PUBLISHED,
        )
        .order_by(MockExam.exam_date, MockExam.id)
    ).all()
    points: dict[uuid.UUID, ProgressPoint] = {}
    for exam_id, name, exam_date, total_net, subject_id, subject_net in rows:
        point = points.setdefault(
            exam_id,
            ProgressPoint(
                mock_exam_id=exam_id, exam_name=name, exam_date=exam_date, net=Decimal(total_net), subjects=[]
            ),
        )
        point.subjects.append(ProgressSubjectNet(subject_id=subject_id, net=subject_net))
    progress = StudentProgress(student_id=student_id, exam_type_id=exam_type_id, points=list(points.values()))
    cache.set_json(key, progress.model_dump(mode="json"))
    return progress
