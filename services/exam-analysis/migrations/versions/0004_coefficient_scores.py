"""coefficient scores: per-net coefficients, minimum-net groups, not-computed reason, linked exam

Estimated scores of TYT/AYT/YDT score types are computed from per-net coefficients (constant +
sum of coefficient x net) instead of the institutional standard scores. Existing scores are
recomputed by `python -m app.cli rescore` after the catalog is seeded again.

Revision ID: 0004
Revises: 0003
Create Date: 2026-11-16 12:00:00.000000
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "0004"
down_revision: str | None = "0003"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.add_column("score_type", sa.Column("coefficient_year", sa.SmallInteger(), nullable=True))
    op.add_column("score_type", sa.Column("minimum_net", sa.Numeric(precision=4, scale=2), nullable=True))
    op.add_column("score_type", sa.Column("max_score", sa.Numeric(precision=7, scale=3), nullable=True))
    op.add_column("score_type_weight", sa.Column("minimum_net_group", sa.SmallInteger(), nullable=True))
    op.add_column("student_score", sa.Column("not_computed_reason", sa.String(length=32), nullable=True))
    op.add_column("student_score", sa.Column("linked_mock_exam_id", sa.Uuid(), nullable=True))
    op.create_foreign_key(
        op.f("fk_student_score_linked_mock_exam_id_mock_exam"),
        "student_score",
        "mock_exam",
        ["linked_mock_exam_id"],
        ["id"],
        ondelete="SET NULL",
    )


def downgrade() -> None:
    op.drop_constraint(
        op.f("fk_student_score_linked_mock_exam_id_mock_exam"), "student_score", type_="foreignkey"
    )
    op.drop_column("student_score", "linked_mock_exam_id")
    op.drop_column("student_score", "not_computed_reason")
    op.drop_column("score_type_weight", "minimum_net_group")
    op.drop_column("score_type", "max_score")
    op.drop_column("score_type", "minimum_net")
    op.drop_column("score_type", "coefficient_year")
