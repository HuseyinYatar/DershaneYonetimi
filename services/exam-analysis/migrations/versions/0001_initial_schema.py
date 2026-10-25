"""initial schema

Revision ID: 0001
Revises:
Create Date: 2026-10-17 16:06:15.500538
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision: str = "0001"
down_revision: str | None = None
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "exam_type",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("code", sa.String(length=16), nullable=False),
        sa.Column("name", sa.String(length=100), nullable=False),
        sa.Column("wrong_divisor", sa.Numeric(precision=4, scale=2), nullable=False),
        sa.CheckConstraint("wrong_divisor > 0", name=op.f("ck_exam_type_wrong_divisor_positive")),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_exam_type")),
        sa.UniqueConstraint("code", name=op.f("uq_exam_type_code")),
    )
    op.create_table(
        "optical_format",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("code", sa.String(length=64), nullable=False),
        sa.Column("name", sa.String(length=100), nullable=False),
        sa.Column("field_definition", postgresql.JSONB(astext_type=sa.Text()), nullable=False),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_optical_format")),
        sa.UniqueConstraint("code", name=op.f("uq_optical_format_code")),
    )
    op.create_table(
        "outbox_event",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("event_type", sa.String(length=100), nullable=False),
        sa.Column("envelope", postgresql.JSONB(astext_type=sa.Text()), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("published_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("attempts", sa.Integer(), nullable=False),
        sa.Column("last_error", sa.Text(), nullable=True),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_outbox_event")),
    )
    op.create_index(
        "ix_outbox_event_unpublished",
        "outbox_event",
        ["created_at"],
        unique=False,
        postgresql_where=sa.text("published_at IS NULL"),
    )
    op.create_table(
        "student_replica",
        sa.Column("student_id", sa.Uuid(), nullable=False),
        sa.Column("student_no", sa.String(length=32), nullable=False),
        sa.Column("branch_id", sa.Uuid(), nullable=False),
        sa.Column("class_id", sa.Uuid(), nullable=False),
        sa.Column("full_name", sa.String(length=200), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.PrimaryKeyConstraint("student_id", name=op.f("pk_student_replica")),
        sa.UniqueConstraint("branch_id", "student_no", name=op.f("uq_student_replica_branch_id_student_no")),
    )
    op.create_table(
        "weak_topic_setting",
        sa.Column("branch_id", sa.Uuid(), nullable=False),
        sa.Column("window_size", sa.Integer(), nullable=False),
        sa.Column("success_threshold", sa.Numeric(precision=5, scale=4), nullable=False),
        sa.Column("updated_by", sa.Uuid(), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.CheckConstraint(
            "success_threshold > 0 AND success_threshold <= 1",
            name=op.f("ck_weak_topic_setting_threshold_range"),
        ),
        sa.CheckConstraint("window_size > 0", name=op.f("ck_weak_topic_setting_window_size_positive")),
        sa.PrimaryKeyConstraint("branch_id", name=op.f("pk_weak_topic_setting")),
    )
    op.create_table(
        "mock_exam",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("exam_type_id", sa.Uuid(), nullable=False),
        sa.Column("name", sa.String(length=200), nullable=False),
        sa.Column("publisher", sa.String(length=200), nullable=True),
        sa.Column("exam_date", sa.Date(), nullable=False),
        sa.Column("branch_id", sa.Uuid(), nullable=False),
        sa.Column(
            "status",
            sa.Enum(
                "DRAFT",
                "KEY_READY",
                "PROCESSED",
                "PUBLISHED",
                name="exam_status",
                native_enum=False,
                create_constraint=True,
                length=32,
            ),
            nullable=False,
        ),
        sa.Column(
            "scoring_source",
            sa.Enum(
                "INSTITUTIONAL",
                "NATIONAL",
                name="scoring_source",
                native_enum=False,
                create_constraint=True,
                length=32,
            ),
            nullable=False,
        ),
        sa.Column("created_by", sa.Uuid(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.ForeignKeyConstraint(
            ["exam_type_id"], ["exam_type.id"], name=op.f("fk_mock_exam_exam_type_id_exam_type")
        ),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_mock_exam")),
    )
    op.create_index("ix_mock_exam_branch_date", "mock_exam", ["branch_id", "exam_date"], unique=False)
    op.create_index(op.f("ix_mock_exam_exam_type_id"), "mock_exam", ["exam_type_id"], unique=False)
    op.create_table(
        "score_type",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("exam_type_id", sa.Uuid(), nullable=False),
        sa.Column("code", sa.String(length=16), nullable=False),
        sa.Column("name", sa.String(length=100), nullable=False),
        sa.Column("base_score", sa.Numeric(precision=7, scale=3), nullable=True),
        sa.ForeignKeyConstraint(
            ["exam_type_id"], ["exam_type.id"], name=op.f("fk_score_type_exam_type_id_exam_type")
        ),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_score_type")),
        sa.UniqueConstraint("exam_type_id", "code", name=op.f("uq_score_type_exam_type_id_code")),
    )
    op.create_index(op.f("ix_score_type_exam_type_id"), "score_type", ["exam_type_id"], unique=False)
    op.create_table(
        "subject",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("exam_type_id", sa.Uuid(), nullable=False),
        sa.Column("code", sa.String(length=32), nullable=False),
        sa.Column("name", sa.String(length=100), nullable=False),
        sa.ForeignKeyConstraint(
            ["exam_type_id"], ["exam_type.id"], name=op.f("fk_subject_exam_type_id_exam_type")
        ),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_subject")),
        sa.UniqueConstraint("exam_type_id", "code", name=op.f("uq_subject_exam_type_id_code")),
    )
    op.create_index(op.f("ix_subject_exam_type_id"), "subject", ["exam_type_id"], unique=False)
    op.create_table(
        "exam_subject_statistic",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("mock_exam_id", sa.Uuid(), nullable=False),
        sa.Column("subject_id", sa.Uuid(), nullable=False),
        sa.Column(
            "source",
            sa.Enum(
                "INSTITUTIONAL",
                "NATIONAL",
                name="scoring_source",
                native_enum=False,
                create_constraint=True,
                length=32,
            ),
            nullable=False,
        ),
        sa.Column("mean", sa.Numeric(precision=7, scale=3), nullable=False),
        sa.Column("std_dev", sa.Numeric(precision=7, scale=3), nullable=False),
        sa.Column("participant_count", sa.Integer(), nullable=False),
        sa.Column("updated_by", sa.Uuid(), nullable=True),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.ForeignKeyConstraint(
            ["mock_exam_id"],
            ["mock_exam.id"],
            name=op.f("fk_exam_subject_statistic_mock_exam_id_mock_exam"),
            ondelete="CASCADE",
        ),
        sa.ForeignKeyConstraint(
            ["subject_id"], ["subject.id"], name=op.f("fk_exam_subject_statistic_subject_id_subject")
        ),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_exam_subject_statistic")),
        sa.UniqueConstraint(
            "mock_exam_id",
            "subject_id",
            "source",
            name=op.f("uq_exam_subject_statistic_mock_exam_id_subject_id_source"),
        ),
    )
    op.create_table(
        "import_job",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("mock_exam_id", sa.Uuid(), nullable=False),
        sa.Column("optical_format_id", sa.Uuid(), nullable=False),
        sa.Column("file_id", sa.Uuid(), nullable=False),
        sa.Column(
            "source",
            sa.Enum(
                "FILE_UPLOAD",
                "OMR",
                name="import_source",
                native_enum=False,
                create_constraint=True,
                length=32,
            ),
            nullable=False,
        ),
        sa.Column("source_ref", sa.String(length=64), nullable=True),
        sa.Column(
            "status",
            sa.Enum(
                "QUEUED",
                "PROCESSING",
                "COMPLETED",
                "FAILED",
                name="job_status",
                native_enum=False,
                create_constraint=True,
                length=32,
            ),
            nullable=False,
        ),
        sa.Column("total_rows", sa.Integer(), nullable=False),
        sa.Column("succeeded_rows", sa.Integer(), nullable=False),
        sa.Column("failed_rows", sa.Integer(), nullable=False),
        sa.Column("error_message", sa.Text(), nullable=True),
        sa.Column("requested_by", sa.Uuid(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("started_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("finished_at", sa.DateTime(timezone=True), nullable=True),
        sa.ForeignKeyConstraint(
            ["mock_exam_id"],
            ["mock_exam.id"],
            name=op.f("fk_import_job_mock_exam_id_mock_exam"),
            ondelete="CASCADE",
        ),
        sa.ForeignKeyConstraint(
            ["optical_format_id"],
            ["optical_format.id"],
            name=op.f("fk_import_job_optical_format_id_optical_format"),
        ),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_import_job")),
    )
    op.create_index(op.f("ix_import_job_mock_exam_id"), "import_job", ["mock_exam_id"], unique=False)
    op.create_index("ix_import_job_queue", "import_job", ["status", "created_at"], unique=False)
    op.create_table(
        "score_type_weight",
        sa.Column("score_type_id", sa.Uuid(), nullable=False),
        sa.Column("subject_id", sa.Uuid(), nullable=False),
        sa.Column("weight", sa.Numeric(precision=6, scale=3), nullable=False),
        sa.CheckConstraint("weight > 0", name=op.f("ck_score_type_weight_weight_positive")),
        sa.ForeignKeyConstraint(
            ["score_type_id"],
            ["score_type.id"],
            name=op.f("fk_score_type_weight_score_type_id_score_type"),
            ondelete="CASCADE",
        ),
        sa.ForeignKeyConstraint(
            ["subject_id"], ["subject.id"], name=op.f("fk_score_type_weight_subject_id_subject")
        ),
        sa.PrimaryKeyConstraint("score_type_id", "subject_id", name=op.f("pk_score_type_weight")),
    )
    op.create_table(
        "student_result",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("mock_exam_id", sa.Uuid(), nullable=False),
        sa.Column("student_id", sa.Uuid(), nullable=False),
        sa.Column("booklet", sa.String(length=1), nullable=False),
        sa.Column("correct", sa.Integer(), nullable=False),
        sa.Column("wrong", sa.Integer(), nullable=False),
        sa.Column("blank", sa.Integer(), nullable=False),
        sa.Column("net", sa.Numeric(precision=6, scale=2), nullable=False),
        sa.Column("branch_rank", sa.Integer(), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.ForeignKeyConstraint(
            ["mock_exam_id"],
            ["mock_exam.id"],
            name=op.f("fk_student_result_mock_exam_id_mock_exam"),
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_student_result")),
        sa.UniqueConstraint(
            "mock_exam_id", "student_id", name=op.f("uq_student_result_mock_exam_id_student_id")
        ),
    )
    op.create_index(op.f("ix_student_result_student_id"), "student_result", ["student_id"], unique=False)
    op.create_table(
        "topic",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("subject_id", sa.Uuid(), nullable=False),
        sa.Column("name", sa.String(length=200), nullable=False),
        sa.Column("parent_topic_id", sa.Uuid(), nullable=True),
        sa.ForeignKeyConstraint(
            ["parent_topic_id"], ["topic.id"], name=op.f("fk_topic_parent_topic_id_topic")
        ),
        sa.ForeignKeyConstraint(["subject_id"], ["subject.id"], name=op.f("fk_topic_subject_id_subject")),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_topic")),
        sa.UniqueConstraint("subject_id", "name", name=op.f("uq_topic_subject_id_name")),
    )
    op.create_index(op.f("ix_topic_parent_topic_id"), "topic", ["parent_topic_id"], unique=False)
    op.create_index(op.f("ix_topic_subject_id"), "topic", ["subject_id"], unique=False)
    op.create_table(
        "answer_key_item",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("mock_exam_id", sa.Uuid(), nullable=False),
        sa.Column("subject_id", sa.Uuid(), nullable=False),
        sa.Column("topic_id", sa.Uuid(), nullable=False),
        sa.Column("booklet", sa.String(length=1), nullable=False),
        sa.Column("question_no", sa.SmallInteger(), nullable=False),
        sa.Column("correct_answer", sa.String(length=1), nullable=False),
        sa.Column("is_cancelled", sa.Boolean(), nullable=False),
        sa.CheckConstraint("question_no > 0", name=op.f("ck_answer_key_item_question_no_positive")),
        sa.ForeignKeyConstraint(
            ["mock_exam_id"],
            ["mock_exam.id"],
            name=op.f("fk_answer_key_item_mock_exam_id_mock_exam"),
            ondelete="CASCADE",
        ),
        sa.ForeignKeyConstraint(
            ["subject_id"], ["subject.id"], name=op.f("fk_answer_key_item_subject_id_subject")
        ),
        sa.ForeignKeyConstraint(["topic_id"], ["topic.id"], name=op.f("fk_answer_key_item_topic_id_topic")),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_answer_key_item")),
        sa.UniqueConstraint(
            "mock_exam_id",
            "booklet",
            "question_no",
            name=op.f("uq_answer_key_item_mock_exam_id_booklet_question_no"),
        ),
    )
    op.create_table(
        "student_score",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("student_result_id", sa.Uuid(), nullable=False),
        sa.Column("score_type_id", sa.Uuid(), nullable=False),
        sa.Column("estimated_score", sa.Numeric(precision=7, scale=3), nullable=True),
        sa.Column(
            "source",
            sa.Enum(
                "INSTITUTIONAL",
                "NATIONAL",
                name="scoring_source",
                native_enum=False,
                create_constraint=True,
                length=32,
            ),
            nullable=True,
        ),
        sa.Column("branch_rank", sa.Integer(), nullable=True),
        sa.ForeignKeyConstraint(
            ["score_type_id"], ["score_type.id"], name=op.f("fk_student_score_score_type_id_score_type")
        ),
        sa.ForeignKeyConstraint(
            ["student_result_id"],
            ["student_result.id"],
            name=op.f("fk_student_score_student_result_id_student_result"),
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_student_score")),
        sa.UniqueConstraint(
            "student_result_id",
            "score_type_id",
            name=op.f("uq_student_score_student_result_id_score_type_id"),
        ),
    )
    op.create_table(
        "student_topic_status",
        sa.Column("student_id", sa.Uuid(), nullable=False),
        sa.Column("topic_id", sa.Uuid(), nullable=False),
        sa.Column("total_questions", sa.Integer(), nullable=False),
        sa.Column("total_correct", sa.Integer(), nullable=False),
        sa.Column("measured_exam_count", sa.Integer(), nullable=False),
        sa.Column("success_rate", sa.Numeric(precision=5, scale=4), nullable=False),
        sa.Column("is_weak", sa.Boolean(), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.ForeignKeyConstraint(
            ["topic_id"], ["topic.id"], name=op.f("fk_student_topic_status_topic_id_topic")
        ),
        sa.PrimaryKeyConstraint("student_id", "topic_id", name=op.f("pk_student_topic_status")),
    )
    op.create_table(
        "subject_result",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("student_result_id", sa.Uuid(), nullable=False),
        sa.Column("subject_id", sa.Uuid(), nullable=False),
        sa.Column("correct", sa.Integer(), nullable=False),
        sa.Column("wrong", sa.Integer(), nullable=False),
        sa.Column("blank", sa.Integer(), nullable=False),
        sa.Column("net", sa.Numeric(precision=6, scale=2), nullable=False),
        sa.ForeignKeyConstraint(
            ["student_result_id"],
            ["student_result.id"],
            name=op.f("fk_subject_result_student_result_id_student_result"),
            ondelete="CASCADE",
        ),
        sa.ForeignKeyConstraint(
            ["subject_id"], ["subject.id"], name=op.f("fk_subject_result_subject_id_subject")
        ),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_subject_result")),
        sa.UniqueConstraint(
            "student_result_id", "subject_id", name=op.f("uq_subject_result_student_result_id_subject_id")
        ),
    )
    op.create_table(
        "topic_result",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("student_result_id", sa.Uuid(), nullable=False),
        sa.Column("topic_id", sa.Uuid(), nullable=False),
        sa.Column("question_count", sa.Integer(), nullable=False),
        sa.Column("correct", sa.Integer(), nullable=False),
        sa.Column("success_rate", sa.Numeric(precision=5, scale=4), nullable=False),
        sa.ForeignKeyConstraint(
            ["student_result_id"],
            ["student_result.id"],
            name=op.f("fk_topic_result_student_result_id_student_result"),
            ondelete="CASCADE",
        ),
        sa.ForeignKeyConstraint(["topic_id"], ["topic.id"], name=op.f("fk_topic_result_topic_id_topic")),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_topic_result")),
        sa.UniqueConstraint(
            "student_result_id", "topic_id", name=op.f("uq_topic_result_student_result_id_topic_id")
        ),
    )
    op.create_table(
        "unmatched_row",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("import_job_id", sa.Uuid(), nullable=False),
        sa.Column("line_no", sa.Integer(), nullable=False),
        sa.Column("raw_student_no", sa.String(length=64), nullable=False),
        sa.Column("raw_line", sa.Text(), nullable=False),
        sa.Column(
            "reason",
            sa.Enum(
                "STUDENT_NOT_FOUND",
                "DUPLICATE_STUDENT",
                "INVALID_BOOKLET",
                "MALFORMED_LINE",
                name="unmatched_reason",
                native_enum=False,
                create_constraint=True,
                length=32,
            ),
            nullable=False,
        ),
        sa.Column(
            "status",
            sa.Enum(
                "PENDING",
                "MATCHED",
                "IGNORED",
                name="match_status",
                native_enum=False,
                create_constraint=True,
                length=32,
            ),
            nullable=False,
        ),
        sa.Column("sheet_image_file_id", sa.Uuid(), nullable=True),
        sa.Column("matched_student_id", sa.Uuid(), nullable=True),
        sa.Column("resolved_by", sa.Uuid(), nullable=True),
        sa.Column("resolved_at", sa.DateTime(timezone=True), nullable=True),
        sa.ForeignKeyConstraint(
            ["import_job_id"],
            ["import_job.id"],
            name=op.f("fk_unmatched_row_import_job_id_import_job"),
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_unmatched_row")),
    )
    op.create_index("ix_unmatched_row_job_status", "unmatched_row", ["import_job_id", "status"], unique=False)
    op.create_table(
        "student_answer",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("student_result_id", sa.Uuid(), nullable=False),
        sa.Column("answer_key_item_id", sa.Uuid(), nullable=False),
        sa.Column("question_no", sa.SmallInteger(), nullable=False),
        sa.Column("given_answer", sa.String(length=1), nullable=True),
        sa.Column(
            "status",
            sa.Enum(
                "CORRECT",
                "WRONG",
                "BLANK",
                "CANCELLED",
                name="answer_status",
                native_enum=False,
                create_constraint=True,
                length=32,
            ),
            nullable=False,
        ),
        sa.ForeignKeyConstraint(
            ["answer_key_item_id"],
            ["answer_key_item.id"],
            name=op.f("fk_student_answer_answer_key_item_id_answer_key_item"),
        ),
        sa.ForeignKeyConstraint(
            ["student_result_id"],
            ["student_result.id"],
            name=op.f("fk_student_answer_student_result_id_student_result"),
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_student_answer")),
        sa.UniqueConstraint(
            "student_result_id",
            "answer_key_item_id",
            name=op.f("uq_student_answer_student_result_id_answer_key_item_id"),
        ),
    )
    op.create_index("ix_student_answer_key_item", "student_answer", ["answer_key_item_id"], unique=False)


def downgrade() -> None:
    op.drop_index("ix_student_answer_key_item", table_name="student_answer")
    op.drop_table("student_answer")
    op.drop_index("ix_unmatched_row_job_status", table_name="unmatched_row")
    op.drop_table("unmatched_row")
    op.drop_table("topic_result")
    op.drop_table("subject_result")
    op.drop_table("student_topic_status")
    op.drop_table("student_score")
    op.drop_table("answer_key_item")
    op.drop_index(op.f("ix_topic_subject_id"), table_name="topic")
    op.drop_index(op.f("ix_topic_parent_topic_id"), table_name="topic")
    op.drop_table("topic")
    op.drop_index(op.f("ix_student_result_student_id"), table_name="student_result")
    op.drop_table("student_result")
    op.drop_table("score_type_weight")
    op.drop_index("ix_import_job_queue", table_name="import_job")
    op.drop_index(op.f("ix_import_job_mock_exam_id"), table_name="import_job")
    op.drop_table("import_job")
    op.drop_table("exam_subject_statistic")
    op.drop_index(op.f("ix_subject_exam_type_id"), table_name="subject")
    op.drop_table("subject")
    op.drop_index(op.f("ix_score_type_exam_type_id"), table_name="score_type")
    op.drop_table("score_type")
    op.drop_index(op.f("ix_mock_exam_exam_type_id"), table_name="mock_exam")
    op.drop_index("ix_mock_exam_branch_date", table_name="mock_exam")
    op.drop_table("mock_exam")
    op.drop_table("weak_topic_setting")
    op.drop_table("student_replica")
    op.drop_index(
        "ix_outbox_event_unpublished",
        table_name="outbox_event",
        postgresql_where=sa.text("published_at IS NULL"),
    )
    op.drop_table("outbox_event")
    op.drop_table("optical_format")
    op.drop_table("exam_type")
