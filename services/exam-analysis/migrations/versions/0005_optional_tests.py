"""optional tests: exam types whose tests a student may leave out (AYT)

A test the student answered nothing in was not taken: it gets no subject or topic results, so its
blanks, zero nets and 0% topics do not show up. Existing results keep their rows until the exam is
imported again.

Revision ID: 0005
Revises: 0004
Create Date: 2026-11-16 15:00:00.000000
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision: str = "0005"
down_revision: str | None = "0004"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.add_column(
        "exam_type", sa.Column("optional_tests", postgresql.JSONB(astext_type=sa.Text()), nullable=True)
    )


def downgrade() -> None:
    op.drop_column("exam_type", "optional_tests")
