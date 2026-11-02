-- attendance service schema.
-- Constraint names are referenced by ConstraintErrorCodes; keep them in sync.

CREATE TABLE excuse (
    id            UUID         PRIMARY KEY,
    student_id    UUID         NOT NULL,
    branch_id     UUID         NOT NULL,
    from_date     DATE         NOT NULL,
    to_date       DATE         NOT NULL,
    reason        VARCHAR(500) NOT NULL,
    status        VARCHAR(16)  NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    requested_by  UUID         NOT NULL,
    decided_by    UUID,
    decided_at    TIMESTAMPTZ,
    decision_note VARCHAR(500),
    created_at    TIMESTAMPTZ  NOT NULL,
    created_by    UUID,
    updated_at    TIMESTAMPTZ  NOT NULL,
    updated_by    UUID,
    version       BIGINT       NOT NULL,
    CONSTRAINT ck_excuse_date_range CHECK (to_date >= from_date)
);

CREATE INDEX idx_excuse_student_status ON excuse (student_id, status, from_date);
CREATE INDEX idx_excuse_branch_status ON excuse (branch_id, status);
CREATE INDEX idx_excuse_requested_by ON excuse (requested_by);

CREATE TABLE class_schedule (
    id           UUID        PRIMARY KEY,
    branch_id    UUID        NOT NULL,
    classroom_id UUID        NOT NULL,
    teacher_id   UUID        NOT NULL,
    subject_id   UUID        NOT NULL,
    day_of_week  VARCHAR(9)  NOT NULL CHECK (day_of_week IN
                   ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')),
    starts_at    TIME        NOT NULL,
    ends_at      TIME        NOT NULL,
    valid_from   DATE        NOT NULL,
    valid_to     DATE,
    active       BOOLEAN     NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    created_by   UUID,
    updated_at   TIMESTAMPTZ NOT NULL,
    updated_by   UUID,
    version      BIGINT      NOT NULL,
    CONSTRAINT ck_schedule_time_range CHECK (ends_at > starts_at),
    CONSTRAINT ck_schedule_valid_range CHECK (valid_to IS NULL OR valid_to >= valid_from)
);

CREATE INDEX idx_schedule_branch ON class_schedule (branch_id, active);
CREATE INDEX idx_schedule_active_valid ON class_schedule (active, valid_from, valid_to);

CREATE TABLE class_session (
    id           UUID        PRIMARY KEY,
    schedule_id  UUID        REFERENCES class_schedule (id),
    branch_id    UUID        NOT NULL,
    classroom_id UUID        NOT NULL,
    teacher_id   UUID        NOT NULL,
    subject_id   UUID        NOT NULL,
    session_date DATE        NOT NULL,
    starts_at    TIME        NOT NULL,
    ends_at      TIME        NOT NULL,
    status       VARCHAR(16) NOT NULL CHECK (status IN ('PLANNED', 'COMPLETED', 'CANCELLED', 'EXPIRED')),
    taken_at     TIMESTAMPTZ,
    taken_by     UUID,
    created_at   TIMESTAMPTZ NOT NULL,
    created_by   UUID,
    updated_at   TIMESTAMPTZ NOT NULL,
    updated_by   UUID,
    version      BIGINT      NOT NULL,
    CONSTRAINT uk_session_schedule_date UNIQUE (schedule_id, session_date),
    -- A session never crosses midnight.
    CONSTRAINT ck_session_time_range CHECK (ends_at > starts_at)
);

CREATE INDEX idx_session_classroom_date ON class_session (classroom_id, session_date);
CREATE INDEX idx_session_teacher_date ON class_session (teacher_id, session_date);
CREATE INDEX idx_session_branch_date ON class_session (branch_id, session_date);
CREATE INDEX idx_session_planned ON class_session (session_date) WHERE status = 'PLANNED';

CREATE TABLE attendance_record (
    id         UUID         PRIMARY KEY,
    session_id UUID         NOT NULL REFERENCES class_session (id),
    student_id UUID         NOT NULL,
    status     VARCHAR(16)  NOT NULL CHECK (status IN ('PRESENT', 'ABSENT', 'LATE', 'EXCUSED')),
    marked_at  TIMESTAMPTZ  NOT NULL,
    marked_by  UUID         NOT NULL,
    excuse_id  UUID         REFERENCES excuse (id),
    note       VARCHAR(200),
    created_at TIMESTAMPTZ  NOT NULL,
    created_by UUID,
    updated_at TIMESTAMPTZ  NOT NULL,
    updated_by UUID,
    version    BIGINT       NOT NULL,
    CONSTRAINT uk_attendance_record_session_student UNIQUE (session_id, student_id)
);

CREATE INDEX idx_record_student ON attendance_record (student_id);

-- Append-only audit trail of every status change after the first submission.
CREATE TABLE attendance_record_change (
    id         UUID         PRIMARY KEY,
    record_id  UUID         NOT NULL REFERENCES attendance_record (id),
    old_status VARCHAR(16),
    new_status VARCHAR(16)  NOT NULL,
    changed_by UUID         NOT NULL,
    reason     VARCHAR(500) NOT NULL,
    changed_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_record_change_record ON attendance_record_change (record_id);

-- Copies of data owned by the user service, kept from its events (temporary REST adapter until
-- the SQS consumer exists).
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
