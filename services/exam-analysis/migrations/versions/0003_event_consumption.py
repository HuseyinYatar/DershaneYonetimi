"""event consumption: processed_event, student_replica fed by user events

Revision ID: 0003
Revises: 0002
Create Date: 2026-11-02 21:00:00.000000
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "0003"
down_revision: str | None = "0002"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "processed_event",
        sa.Column("event_id", sa.Uuid(), nullable=False),
        sa.Column("event_type", sa.String(length=100), nullable=False),
        sa.Column("processed_at", sa.DateTime(timezone=True), nullable=False),
        sa.PrimaryKeyConstraint("event_id", name=op.f("pk_processed_event")),
    )
    op.alter_column("student_replica", "class_id", existing_type=sa.Uuid(), nullable=True)
    # Existing rows are active; the default is only for this migration, the model sets the value.
    op.add_column(
        "student_replica", sa.Column("is_active", sa.Boolean(), nullable=False, server_default=sa.true())
    )
    op.alter_column("student_replica", "is_active", server_default=None)
    op.add_column("student_replica", sa.Column("record_version", sa.Integer(), nullable=True))
    op.drop_constraint("uq_student_replica_branch_id_student_no", "student_replica", type_="unique")
    op.create_index(
        "uq_student_replica_active_student_no",
        "student_replica",
        ["branch_id", "student_no"],
        unique=True,
        postgresql_where=sa.text("is_active"),
    )


def downgrade() -> None:
    op.drop_index(
        "uq_student_replica_active_student_no",
        table_name="student_replica",
        postgresql_where=sa.text("is_active"),
    )
    op.create_unique_constraint(
        "uq_student_replica_branch_id_student_no", "student_replica", ["branch_id", "student_no"]
    )
    op.drop_column("student_replica", "record_version")
    op.drop_column("student_replica", "is_active")
    op.alter_column("student_replica", "class_id", existing_type=sa.Uuid(), nullable=False)
    op.drop_table("processed_event")
