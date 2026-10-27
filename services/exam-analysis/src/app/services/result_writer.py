"""Bulk persistence of scored sheets (Core executemany, no per-row ORM objects)."""

import uuid
from dataclasses import dataclass

import pandas as pd
from sqlalchemy import delete, insert, update
from sqlalchemy.orm import Session

from app.models import StudentAnswer, StudentResult, SubjectResult, TopicResult, UnmatchedRow
from app.models.enums import MatchStatus
from app.repositories.frames import to_records
from app.repositories.results import result_ids_statement
from app.scoring.aggregation import aggregate_subjects, aggregate_topics, aggregate_totals
from app.services.sheet_scoring import ScoredSheets

SUBJECT_COLUMNS = ["id", "student_result_id", "subject_id", "correct", "wrong", "blank", "net"]
TOPIC_COLUMNS = ["id", "student_result_id", "topic_id", "question_count", "correct", "success_rate"]
ANSWER_COLUMNS = ["id", "student_result_id", "answer_key_item_id", "question_no", "given_answer", "status"]


def _new_ids(count: int) -> list[uuid.UUID]:
    """Returns `count` new random UUIDs."""
    return [uuid.uuid4() for _ in range(count)]


def insert_results( session: Session, *, mock_exam_id: uuid.UUID, matched: pd.DataFrame, scored: ScoredSheets) -> pd.Series:
    """Bulk-writes scored sheets to student_result, subject_result, topic_result and student_answer.
    Returns a map from sheet_key to the new student_result id.
    """
    # ogrenciler uuid atamasi
    result_ids = pd.Series(_new_ids(len(matched)), index=matched.index, dtype=object)
    sheet_keys = scored.totals["sheet_key"]
    results = scored.totals.assign(
        id=sheet_keys.map(result_ids),
        mock_exam_id=mock_exam_id,
        student_id=sheet_keys.map(matched["student_id"]),
        booklet=sheet_keys.map(matched["booklet"]),
        net=scored.totals["net"].round(2),
    )
    session.execute(
        insert(StudentResult),
        to_records(
            results[["id", "mock_exam_id", "student_id", "booklet", "correct", "wrong", "blank", "net"]]
        ),
    )
    _insert_subjects_and_topics(session, result_ids, scored.subjects, scored.topics)
    answers = scored.graded.assign(
        id=_new_ids(len(scored.graded)), student_result_id=scored.graded["sheet_key"].map(result_ids)
    )
    session.execute(insert(StudentAnswer), to_records(answers[ANSWER_COLUMNS]))
    return result_ids


def _insert_subjects_and_topics(
    session: Session, result_ids: pd.Series, subjects: pd.DataFrame, topics: pd.DataFrame
) -> None:
    """Bulk-inserts subject_result and topic_result rows, each linked to its student_result."""
    subject_rows = subjects.assign(
        id=_new_ids(len(subjects)),
        student_result_id=subjects["sheet_key"].map(result_ids),
        net=subjects["net"].round(2),
    )
    topic_rows = topics.assign(
        id=_new_ids(len(topics)),
        student_result_id=topics["sheet_key"].map(result_ids),
        success_rate=topics["success_rate"].round(4),
    )
    # executemany needs at least one row; a key whose questions are all cancelled has no topic rows.
    if not subject_rows.empty:
        session.execute(insert(SubjectResult), to_records(subject_rows[SUBJECT_COLUMNS]))
    if not topic_rows.empty:
        session.execute(insert(TopicResult), to_records(topic_rows[TOPIC_COLUMNS]))


@dataclass(frozen=True)
class UnmatchedInsertOutcome:
    """What insert_unmatched_rows wrote."""

    inserted: int
    # sheet_ref values that were present but not a file id (reported by the caller).
    invalid_sheet_refs: int


def insert_unmatched_rows(
    session: Session, import_job_id: uuid.UUID, unmatched: pd.DataFrame
) -> UnmatchedInsertOutcome:
    """Saves rows that could not be scored to unmatched_row as PENDING.

    The reason and raw line are kept so an admin can match or ignore the row later.
    """
    sheet_refs = unmatched["sheet_ref"]
    sheet_image_ids = pd.Series(
        [_as_uuid(value) for value in sheet_refs], index=unmatched.index, dtype=object
    )
    invalid_refs = int((sheet_refs.notna() & sheet_image_ids.isna()).sum())
    rows = pd.DataFrame(
        {
            "id": _new_ids(len(unmatched)),
            "import_job_id": import_job_id,
            "line_no": unmatched["line_no"].to_numpy(),
            "raw_student_no": unmatched["student_no"].to_numpy(),
            "raw_line": unmatched["raw_line"].to_numpy(),
            "reason": unmatched["reason"].to_numpy(),
            "status": MatchStatus.PENDING.value,
            "sheet_image_file_id": sheet_image_ids.to_numpy(),
        }
    )
    if not rows.empty:
        session.execute(insert(UnmatchedRow), to_records(rows))
    return UnmatchedInsertOutcome(inserted=len(rows), invalid_sheet_refs=invalid_refs)


def _as_uuid(value: object) -> uuid.UUID | None:
    """Converts a sheet_ref to a UUID; returns None when it is empty or not a UUID."""
    if value is None or pd.isna(value):
        return None
    try:
        return uuid.UUID(str(value))
    except ValueError:
        return None


def rewrite_after_regrade(
    session: Session, mock_exam_id: uuid.UUID, regraded: pd.DataFrame, wrong_divisor: float
) -> int:
    """Applies new answer statuses after an answer key change and rebuilds all aggregates.

    regraded: answer_id, sheet_key (= student_result_id), subject_id, topic_id, previous_status,
    status. Returns the number of answers whose status changed.
    """
    changed = regraded[regraded["status"] != regraded["previous_status"]]
    if not changed.empty:
        session.execute(
            update(StudentAnswer),
            to_records(changed[["answer_id", "status"]].rename(columns={"answer_id": "id"})),
        )

    subjects = aggregate_subjects(regraded, wrong_divisor)
    totals = aggregate_totals(subjects)
    result_ids_query = result_ids_statement(mock_exam_id)
    for model in (SubjectResult, TopicResult):
        session.execute(
            delete(model)
            .where(model.student_result_id.in_(result_ids_query))
            .execution_options(synchronize_session=False)
        )
    identity = pd.Series(totals["sheet_key"].to_numpy(), index=totals["sheet_key"].to_numpy(), dtype=object)
    _insert_subjects_and_topics(session, identity, subjects, aggregate_topics(regraded))
    session.execute(
        update(StudentResult),
        to_records(
            totals.assign(net=totals["net"].round(2)).rename(columns={"sheet_key": "id"})[
                ["id", "correct", "wrong", "blank", "net"]
            ]
        ),
    )
    return len(changed)
