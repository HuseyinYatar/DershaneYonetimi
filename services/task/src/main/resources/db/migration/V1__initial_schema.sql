-- task service schema.
-- Constraint names are referenced by ConstraintErrorCodes; keep them in sync.

CREATE TABLE task (
    id                  UUID          PRIMARY KEY,
    branch_id           UUID          NOT NULL,
    teacher_id          UUID          NOT NULL,
    title               VARCHAR(200)  NOT NULL,
    description         VARCHAR(5000) NOT NULL DEFAULT '',
    target_type         VARCHAR(16)   NOT NULL CHECK (target_type IN ('STUDENT', 'CLASSROOM')),
    target_id           UUID          NOT NULL,
    recurrence          VARCHAR(16)   NOT NULL CHECK (recurrence IN ('ONCE', 'DAILY', 'WEEKLY')),
    recurrence_days     VARCHAR(9)[]  NOT NULL DEFAULT '{}',
    start_date          DATE          NOT NULL,
    end_date            DATE,
    due_time            TIME          NOT NULL,
    attachment_file_ids UUID[]        NOT NULL DEFAULT '{}',
    active              BOOLEAN       NOT NULL,
    created_at          TIMESTAMPTZ   NOT NULL,
    created_by          UUID,
    updated_at          TIMESTAMPTZ   NOT NULL,
    updated_by          UUID,
    version             BIGINT        NOT NULL,
    CONSTRAINT ck_task_once_has_end CHECK (recurrence <> 'ONCE' OR end_date IS NOT NULL),
    CONSTRAINT ck_task_weekly_has_days CHECK (recurrence <> 'WEEKLY' OR cardinality(recurrence_days) > 0),
    CONSTRAINT ck_task_date_range CHECK (end_date IS NULL OR end_date >= start_date)
);

CREATE INDEX idx_task_teacher ON task (teacher_id);
CREATE INDEX idx_task_branch ON task (branch_id);
CREATE INDEX idx_task_target ON task (target_type, target_id) WHERE active;

CREATE TABLE assignment (
    id              UUID         PRIMARY KEY,
    task_id         UUID         NOT NULL REFERENCES task (id),
    student_id      UUID         NOT NULL,
    occurrence_date DATE         NOT NULL,
    due_at          TIMESTAMPTZ  NOT NULL,
    status          VARCHAR(16)  NOT NULL CHECK (status IN
                      ('ASSIGNED', 'SUBMITTED', 'OVERDUE', 'GRADED', 'CANCELLED')),
    reminder_sent   BOOLEAN      NOT NULL,
    assigned_at     TIMESTAMPTZ  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    created_by      UUID,
    updated_at      TIMESTAMPTZ  NOT NULL,
    updated_by      UUID,
    version         BIGINT       NOT NULL,
    CONSTRAINT uk_assignment_task_student_date UNIQUE (task_id, student_id, occurrence_date)
);

CREATE INDEX idx_assignment_student_due ON assignment (student_id, due_at);
CREATE INDEX idx_assignment_task ON assignment (task_id, occurrence_date);
-- The CronJob queries (reminders and overdue marking) only look at open assignments.
CREATE INDEX idx_assignment_open_due ON assignment (due_at) WHERE status = 'ASSIGNED';

CREATE TABLE submission (
    id            UUID        PRIMARY KEY,
    assignment_id UUID        NOT NULL UNIQUE REFERENCES assignment (id),
    description   TEXT,
    file_ids      UUID[]      NOT NULL DEFAULT '{}',
    submitted_at  TIMESTAMPTZ NOT NULL,
    late          BOOLEAN     NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL,
    created_by    UUID,
    updated_at    TIMESTAMPTZ NOT NULL,
    updated_by    UUID,
    version       BIGINT      NOT NULL
);

CREATE TABLE evaluation (
    id           UUID        PRIMARY KEY,
    submission_id UUID       NOT NULL UNIQUE REFERENCES submission (id),
    evaluator_id UUID        NOT NULL,
    score        INTEGER     CHECK (score BETWEEN 0 AND 100),
    feedback     TEXT,
    evaluated_at TIMESTAMPTZ NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    created_by   UUID,
    updated_at   TIMESTAMPTZ NOT NULL,
    updated_by   UUID,
    version      BIGINT      NOT NULL,
    CONSTRAINT ck_evaluation_not_empty CHECK (score IS NOT NULL OR (feedback IS NOT NULL AND feedback <> ''))
);

-- Copies of data owned by the user service, kept from its events (an admin REST adapter repairs them).
CREATE TABLE membership_replica (
    id             UUID         PRIMARY KEY,
    classroom_id   UUID         NOT NULL,
    student_id     UUID         NOT NULL,
    branch_id      UUID         NOT NULL,
    start_date     DATE         NOT NULL,
    end_date       DATE,
    student_number VARCHAR(32)  NOT NULL,
    full_name      VARCHAR(200) NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    created_by     UUID,
    updated_at     TIMESTAMPTZ  NOT NULL,
    updated_by     UUID,
    version        BIGINT       NOT NULL
);

CREATE INDEX idx_membership_classroom ON membership_replica (classroom_id, start_date, end_date);
CREATE INDEX idx_membership_student ON membership_replica (student_id);

CREATE TABLE guardian_link_replica (
    student_id  UUID        PRIMARY KEY,
    guardian_id UUID        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    created_by  UUID,
    updated_at  TIMESTAMPTZ NOT NULL,
    updated_by  UUID,
    version     BIGINT      NOT NULL
);

CREATE INDEX idx_guardian_link_guardian ON guardian_link_replica (guardian_id);

CREATE TABLE outbox_event (
    event_id      UUID        PRIMARY KEY,
    event_type    VARCHAR(64) NOT NULL,
    aggregate_id  UUID        NOT NULL,
    payload       JSONB       NOT NULL,
    traceparent   VARCHAR(64),
    created_at    TIMESTAMPTZ NOT NULL,
    published_at  TIMESTAMPTZ,
    attempt_count INTEGER     NOT NULL DEFAULT 0,
    last_error    VARCHAR(500)
);

CREATE INDEX idx_outbox_pending ON outbox_event (created_at) WHERE published_at IS NULL;
CREATE INDEX idx_outbox_published ON outbox_event (published_at) WHERE published_at IS NOT NULL;

CREATE TABLE idempotency_record (
    id              UUID         PRIMARY KEY,
    caller_id       UUID         NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash    VARCHAR(64)  NOT NULL,
    status          VARCHAR(16)  NOT NULL CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
    response_status INTEGER,
    response_body   JSONB,
    created_at      TIMESTAMPTZ  NOT NULL,
    expires_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_idempotency_caller_key UNIQUE (caller_id, idempotency_key)
);

CREATE INDEX idx_idempotency_expires ON idempotency_record (expires_at);

-- Events already applied from the SQS queue. SQS delivers at least once, so the consumer inserts the
-- event id in the same transaction as the change and skips an event whose id is already here.
CREATE TABLE processed_event (
    event_id     UUID        PRIMARY KEY,
    event_type   VARCHAR(64) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
