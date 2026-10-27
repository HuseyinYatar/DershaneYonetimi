import uuid
from collections.abc import Collection

import pandas as pd
from sqlalchemy import select, tuple_
from sqlalchemy.orm import Session

from app.models import StudentReplica


def student_ids_by_no(session: Session, branch_id: uuid.UUID) -> pd.Series:
    """Student number -> student id for one branch (student numbers are unique per branch)."""
    rows = session.execute(
        select(StudentReplica.student_no, StudentReplica.student_id).where(
            StudentReplica.branch_id == branch_id
        )
    ).all()
    return pd.Series(dict(rows), dtype=object)


def number_holders(
    session: Session, numbers: Collection[tuple[uuid.UUID, str]]
) -> dict[tuple[uuid.UUID, str], uuid.UUID]:
    """Current student of each (branch_id, student_no) pair that is already taken."""
    rows = session.execute(
        select(StudentReplica.branch_id, StudentReplica.student_no, StudentReplica.student_id).where(
            tuple_(StudentReplica.branch_id, StudentReplica.student_no).in_(list(numbers))
        )
    ).all()
    return {(branch_id, student_no): student_id for branch_id, student_no, student_id in rows}


def branch_student_ids(session: Session, branch_id: uuid.UUID) -> list[uuid.UUID]:
    return list(
        session.scalars(select(StudentReplica.student_id).where(StudentReplica.branch_id == branch_id))
    )


def full_names(session: Session, student_ids: list[uuid.UUID]) -> dict[uuid.UUID, str]:
    rows = session.execute(
        select(StudentReplica.student_id, StudentReplica.full_name).where(
            StudentReplica.student_id.in_(student_ids)
        )
    ).all()
    return dict(rows)
