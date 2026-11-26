"""Initial schema: file metadata and who a file is shared with.

Revision ID: 0001
Revises:
Create Date: 2026-11-16
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "0001"
down_revision: str | None = None
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None

PURPOSES = ("TASK_ATTACHMENT", "SUBMISSION", "OMR_RESULT")
KINDS = ("JPEG", "PNG", "WEBP", "PDF", "TEXT")


def _in(column: str, values: tuple[str, ...]) -> str:
    return f"{column} IN ({', '.join(repr(value) for value in values)})"


def upgrade() -> None:
    op.create_table(
        "file",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("purpose", sa.String(32), nullable=False),
        sa.Column("owner_id", sa.Uuid(), nullable=False),
        sa.Column("kind", sa.String(32), nullable=False),
        sa.Column("size_bytes", sa.BigInteger(), nullable=False),
        sa.Column("file_name", sa.String(255), nullable=False),
        sa.Column("source_sha256", sa.String(64), nullable=False),
        sa.Column("idempotency_key", sa.String(255), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.CheckConstraint(_in("purpose", PURPOSES), name=op.f("ck_file_file_purpose")),
        sa.CheckConstraint(_in("kind", KINDS), name=op.f("ck_file_file_kind")),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_file")),
        sa.UniqueConstraint("owner_id", "idempotency_key", name=op.f("uq_file_owner_id_idempotency_key")),
    )
    op.create_table(
        "file_access",
        sa.Column("file_id", sa.Uuid(), nullable=False),
        sa.Column("user_id", sa.Uuid(), nullable=False),
        sa.ForeignKeyConstraint(["file_id"], ["file.id"], name=op.f("fk_file_access_file_id_file")),
        sa.PrimaryKeyConstraint("file_id", "user_id", name=op.f("pk_file_access")),
    )


def downgrade() -> None:
    op.drop_table("file_access")
    op.drop_table("file")
