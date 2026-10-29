"""Maintenance of the local student copy.

Today an admin endpoint feeds it; in week 4 the StudentAddedToClass consumer calls upsert()
with the same rows, so both paths share one implementation.
"""

from collections import Counter
from collections.abc import Sequence

import structlog
from sqlalchemy.dialects.postgresql import insert as pg_insert
from sqlalchemy.orm import Session

from app.auth.access import ensure_branch_staff
from app.auth.keycloak import CurrentUser
from app.errors import BusinessRuleError, ConflictError
from app.models import StudentReplica
from app.models.base import utcnow
from app.repositories import replicas as replicas_repo
from app.schemas.replicas import StudentReplicaBulkUpsert, StudentReplicaIn

logger = structlog.get_logger(__name__)


def upsert_from_admin(session: Session, user: CurrentUser, request: StudentReplicaBulkUpsert) -> int:
    """Temporary admin endpoint: writes student copies of the admin's own branch only.

    Returns the number of students written.
    """
    for branch_id in {student.branch_id for student in request.students}:
        ensure_branch_staff(user, branch_id)
    upserted = upsert(session, request.students)
    session.commit()
    logger.info("student_replica.bulk_upserted", students=upserted, requested_by=str(user.id))
    return upserted


def upsert(session: Session, students: Sequence[StudentReplicaIn]) -> int:
    """Inserts new students or updates existing ones (matched by student_id).

    Rejects a request that lists a student twice, gives two students of one branch the same
    number, or gives a student a number another student of the branch already has. Does not
    commit; the caller does.
    """
    duplicate_ids = [sid for sid, count in Counter(s.student_id for s in students).items() if count > 1]
    if duplicate_ids:
        raise BusinessRuleError(
            "DUPLICATE_STUDENT_IN_REQUEST",
            "A student appears more than once",
            student_ids=[str(student_id) for student_id in duplicate_ids],
        )
    numbers = Counter((s.branch_id, s.student_no) for s in students)
    duplicate_numbers = sum(1 for count in numbers.values() if count > 1)
    if duplicate_numbers:
        # The numbers themselves are personal data and stay out of the response and logs.
        raise BusinessRuleError(
            "DUPLICATE_STUDENT_NO_IN_REQUEST",
            "A student number is used by more than one student of the same branch",
            duplicate_count=duplicate_numbers,
        )
    holders = replicas_repo.number_holders(session, numbers.keys())
    taken = [
        s.student_id
        for s in students
        if holders.get((s.branch_id, s.student_no), s.student_id) != s.student_id
    ]
    if taken:
        # Ids of the rejected entries tell the admin what to fix; the numbers stay out (personal data).
        raise ConflictError(
            "STUDENT_NO_TAKEN",
            "A student number already belongs to another student of the same branch",
            student_ids=[str(student_id) for student_id in taken],
        )

    now = utcnow()
    statement = pg_insert(StudentReplica).values(
        [{**student.model_dump(), "updated_at": now} for student in students]
    )
    statement = statement.on_conflict_do_update(
        index_elements=[StudentReplica.student_id],
        set_={
            "student_no": statement.excluded.student_no,
            "branch_id": statement.excluded.branch_id,
            "class_id": statement.excluded.class_id,
            "full_name": statement.excluded.full_name,
            "updated_at": statement.excluded.updated_at,
        },
    )
    session.execute(statement)
    return len(students)
