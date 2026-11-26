"""Two new purposes: OMR_SHEET (answer sheet photo) and MESSAGE_PHOTO (chat photo).

The purpose column is a VARCHAR with a CHECK constraint, so a new value only replaces the constraint.

Revision ID: 0002
Revises: 0001
Create Date: 2026-11-26
"""

from collections.abc import Sequence

from alembic import op

revision: str = "0002"
down_revision: str | None = "0001"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None

CONSTRAINT = "ck_file_file_purpose"
OLD_PURPOSES = ("TASK_ATTACHMENT", "SUBMISSION", "OMR_RESULT")
NEW_PURPOSES = (*OLD_PURPOSES, "OMR_SHEET", "MESSAGE_PHOTO")


def _in(values: tuple[str, ...]) -> str:
    return f"purpose IN ({', '.join(repr(value) for value in values)})"


def upgrade() -> None:
    op.drop_constraint(op.f(CONSTRAINT), "file", type_="check")
    op.create_check_constraint(op.f(CONSTRAINT), "file", _in(NEW_PURPOSES))


def downgrade() -> None:
    # Fails while rows with a new purpose exist; they have to be removed first.
    op.drop_constraint(op.f(CONSTRAINT), "file", type_="check")
    op.create_check_constraint(op.f(CONSTRAINT), "file", _in(OLD_PURPOSES))
