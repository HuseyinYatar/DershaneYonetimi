"""outbox traceparent column

The shared envelope schema allows no extra fields, so the trace context moves out of the envelope
into its own column and travels as an SNS message attribute.

Revision ID: 0002
Revises: 0001
Create Date: 2026-10-25 21:30:00.000000
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "0002"
down_revision: str | None = "0001"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.add_column("outbox_event", sa.Column("traceparent", sa.String(length=64), nullable=True))


def downgrade() -> None:
    op.drop_column("outbox_event", "traceparent")
