-- messaging service schema.
-- Constraint names are referenced by ConstraintErrorCodes; keep them in sync.
-- Entities extend AuditableJpaEntity (created_at/by, updated_at/by, version), so message.updated_at
-- is the time of the last edit or delete and drives the client sync query.

-- Copy of the user service's student/teacher data, kept from UserUpdated events.
CREATE TABLE user_replica (
    user_id        UUID         PRIMARY KEY,
    role           VARCHAR(16)  NOT NULL CHECK (role IN ('STUDENT', 'TEACHER')),
    branch_id      UUID         NOT NULL,
    first_name     VARCHAR(100) NOT NULL,
    last_name      VARCHAR(100) NOT NULL,
    active         BOOLEAN      NOT NULL,
    record_version BIGINT       NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    created_by     UUID,
    updated_at     TIMESTAMPTZ  NOT NULL,
    updated_by     UUID,
    version        BIGINT       NOT NULL
);

CREATE TABLE conversation (
    id              UUID        PRIMARY KEY,
    branch_id       UUID        NOT NULL,
    student_id      UUID        NOT NULL,
    teacher_id      UUID        NOT NULL,
    last_seq        BIGINT      NOT NULL DEFAULT 0,
    last_message_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL,
    created_by      UUID,
    updated_at      TIMESTAMPTZ NOT NULL,
    updated_by      UUID,
    version         BIGINT      NOT NULL,
    CONSTRAINT uq_conversation_pair UNIQUE (student_id, teacher_id)
);

CREATE TABLE conversation_participant (
    conversation_id UUID   NOT NULL REFERENCES conversation (id),
    user_id         UUID   NOT NULL,
    last_read_seq   BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (conversation_id, user_id)
);

CREATE INDEX ix_participant_user ON conversation_participant (user_id);

CREATE TABLE message (
    id              UUID        PRIMARY KEY,
    conversation_id UUID        NOT NULL REFERENCES conversation (id),
    seq             BIGINT      NOT NULL,
    sender_id       UUID        NOT NULL,
    type            VARCHAR(8)  NOT NULL CHECK (type IN ('TEXT', 'PHOTO')),
    content         TEXT,
    file_id         UUID,
    sent_at         TIMESTAMPTZ NOT NULL,
    edited_at       TIMESTAMPTZ,
    deleted         BOOLEAN     NOT NULL DEFAULT FALSE,
    deleted_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL,
    created_by      UUID,
    updated_at      TIMESTAMPTZ NOT NULL,
    updated_by      UUID,
    version         BIGINT      NOT NULL,
    CONSTRAINT uq_message_seq UNIQUE (conversation_id, seq),
    CONSTRAINT ck_message_body CHECK (
        (type = 'TEXT' AND content IS NOT NULL) OR (type = 'PHOTO' AND file_id IS NOT NULL))
);

CREATE INDEX ix_message_updated ON message (conversation_id, updated_at);

-- Append-only audit trail of edits.
CREATE TABLE message_edit (
    id               UUID        PRIMARY KEY,
    message_id       UUID        NOT NULL REFERENCES message (id),
    previous_content TEXT,
    edited_at        TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_message_edit_message ON message_edit (message_id);

CREATE TABLE message_report (
    id              UUID        PRIMARY KEY,
    message_id      UUID        NOT NULL REFERENCES message (id),
    conversation_id UUID        NOT NULL REFERENCES conversation (id),
    branch_id       UUID        NOT NULL,
    reporter_id     UUID        NOT NULL,
    reason          TEXT        NOT NULL,
    status          VARCHAR(8)  NOT NULL CHECK (status IN ('OPEN', 'CLOSED')),
    reviewed_by     UUID,
    reviewed_at     TIMESTAMPTZ,
    resolution_note TEXT,
    created_at      TIMESTAMPTZ NOT NULL,
    created_by      UUID,
    updated_at      TIMESTAMPTZ NOT NULL,
    updated_by      UUID,
    version         BIGINT      NOT NULL,
    CONSTRAINT uq_report_message_reporter UNIQUE (message_id, reporter_id)
);

CREATE INDEX ix_report_branch_status ON message_report (branch_id, status);

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
