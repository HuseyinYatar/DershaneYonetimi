"""Branch and student scoping rules shared by the use cases."""

import uuid

from sqlalchemy.orm import Session

from app.auth.keycloak import CurrentUser
from app.errors import ForbiddenError, NotFoundError
from app.models import StudentReplica
from app.models.enums import Role


def ensure_branch_staff(user: CurrentUser, branch_id: uuid.UUID) -> None:
    """Teachers and admins act only on their own branch (branch_id claim in the token)."""
    if not user.has_any_role(Role.ADMIN, Role.TEACHER):
        raise ForbiddenError("ROLE_NOT_ALLOWED", "Only teachers and admins can access branch data")
    if user.branch_id is None:
        raise ForbiddenError("BRANCH_CLAIM_MISSING", "Token carries no branch_id claim")
    if user.branch_id != branch_id:
        raise ForbiddenError(
            "BRANCH_NOT_ALLOWED", "The resource belongs to another branch", branch_id=str(branch_id)
        )


def ensure_can_view_student(session: Session, user: CurrentUser, student_id: uuid.UUID) -> None:
    """Students see themselves; teachers and admins see students of their branch.
    Parent access needs the parent-student link owned by the user service and is not available yet.
    """
    if user.has_any_role(Role.STUDENT) and user.id == student_id:
        return
    if user.has_any_role(Role.ADMIN, Role.TEACHER):
        replica = session.get(StudentReplica, student_id)
        if replica is None:
            raise NotFoundError(
                "STUDENT_NOT_FOUND", "Student is unknown to this service", student_id=str(student_id)
            )
        ensure_branch_staff(user, replica.branch_id)
        return
    if user.has_any_role(Role.PARENT):
        raise ForbiddenError("PARENT_ACCESS_NOT_AVAILABLE", "Parent access is not available yet")
    raise ForbiddenError(
        "STUDENT_NOT_ALLOWED", "You can only view your own results", student_id=str(student_id)
    )
