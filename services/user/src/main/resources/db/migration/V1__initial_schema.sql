-- user service schema.
-- Constraint names are referenced by ConstraintErrorCodes; keep them in sync.

CREATE TABLE branch (
    id          UUID         PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    code        VARCHAR(16)  NOT NULL,
    active      BOOLEAN      NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    created_by  UUID,
    updated_at  TIMESTAMPTZ  NOT NULL,
    updated_by  UUID,
    version     BIGINT       NOT NULL,
    CONSTRAINT uk_branch_code UNIQUE (code)
);

CREATE TABLE subject (
    id          UUID         PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    code        VARCHAR(16)  NOT NULL,
    active      BOOLEAN      NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    created_by  UUID,
    updated_at  TIMESTAMPTZ  NOT NULL,
    updated_by  UUID,
    version     BIGINT       NOT NULL,
    CONSTRAINT uk_subject_code UNIQUE (code)
);

CREATE TABLE classroom (
    id          UUID         PRIMARY KEY,
    branch_id   UUID         NOT NULL REFERENCES branch (id),
    name        VARCHAR(60)  NOT NULL,
    grade_level INTEGER      NOT NULL CHECK (grade_level BETWEEN 1 AND 13),
    term        VARCHAR(9)   NOT NULL,
    active      BOOLEAN      NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    created_by  UUID,
    updated_at  TIMESTAMPTZ  NOT NULL,
    updated_by  UUID,
    version     BIGINT       NOT NULL,
    CONSTRAINT uk_classroom_branch_term_name UNIQUE (branch_id, term, name)
);

-- users.id is the Keycloak "sub" of the account. "user" is reserved in PostgreSQL.
CREATE TABLE users (
    id          UUID         PRIMARY KEY,
    branch_id   UUID         NOT NULL REFERENCES branch (id),
    role        VARCHAR(16)  NOT NULL CHECK (role IN ('STUDENT', 'TEACHER', 'GUARDIAN', 'ADMIN')),
    first_name  VARCHAR(80)  NOT NULL,
    last_name   VARCHAR(80)  NOT NULL,
    email       VARCHAR(254),
    phone       VARCHAR(20),
    username    VARCHAR(100) NOT NULL,
    active      BOOLEAN      NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    created_by  UUID,
    updated_at  TIMESTAMPTZ  NOT NULL,
    updated_by  UUID,
    version     BIGINT       NOT NULL,
    CONSTRAINT uk_users_username UNIQUE (username),
    -- Target of the composite foreign key from student (user_id, branch_id).
    CONSTRAINT uk_users_id_branch UNIQUE (id, branch_id)
);

CREATE UNIQUE INDEX uk_users_email ON users (lower(email)) WHERE email IS NOT NULL;
CREATE INDEX idx_users_branch_role ON users (branch_id, role);

-- branch_id is copied from users so that "student number is unique within a branch" can be a
-- database constraint. The composite foreign key keeps the copy equal to users.branch_id.
CREATE TABLE student (
    user_id        UUID        PRIMARY KEY,
    branch_id      UUID        NOT NULL,
    student_number VARCHAR(20) NOT NULL,
    grade_level    INTEGER     NOT NULL CHECK (grade_level BETWEEN 1 AND 13),
    target_exam    VARCHAR(8)  NOT NULL CHECK (target_exam IN ('YKS', 'LGS')),
    CONSTRAINT fk_student_user FOREIGN KEY (user_id, branch_id)
        REFERENCES users (id, branch_id) ON UPDATE CASCADE,
    CONSTRAINT uk_student_branch_number UNIQUE (branch_id, student_number)
);

CREATE TABLE teacher (
    user_id              UUID PRIMARY KEY REFERENCES users (id),
    specialty_subject_id UUID NOT NULL REFERENCES subject (id)
);

CREATE TABLE guardian (
    user_id UUID PRIMARY KEY REFERENCES users (id)
);

-- A student has at most one guardian; a guardian may have several children.
CREATE TABLE student_guardian (
    guardian_id  UUID        NOT NULL REFERENCES guardian (user_id),
    student_id   UUID        NOT NULL REFERENCES student (user_id),
    relationship VARCHAR(16) NOT NULL
        CHECK (relationship IN ('MOTHER', 'FATHER', 'LEGAL_GUARDIAN', 'OTHER')),
    created_at   TIMESTAMPTZ NOT NULL,
    created_by   UUID,
    updated_at   TIMESTAMPTZ NOT NULL,
    updated_by   UUID,
    version      BIGINT      NOT NULL,
    PRIMARY KEY (guardian_id, student_id),
    CONSTRAINT uk_student_guardian_student UNIQUE (student_id)
);

CREATE TABLE classroom_membership (
    id           UUID        PRIMARY KEY,
    student_id   UUID        NOT NULL REFERENCES student (user_id),
    classroom_id UUID        NOT NULL REFERENCES classroom (id),
    start_date   DATE        NOT NULL,
    end_date     DATE,
    created_at   TIMESTAMPTZ NOT NULL,
    created_by   UUID,
    updated_at   TIMESTAMPTZ NOT NULL,
    updated_by   UUID,
    version      BIGINT      NOT NULL,
    CONSTRAINT ck_membership_dates CHECK (end_date IS NULL OR end_date >= start_date)
);

-- A student has at most one active classroom.
CREATE UNIQUE INDEX uk_membership_active_student
    ON classroom_membership (student_id) WHERE end_date IS NULL;
CREATE INDEX idx_membership_active_classroom
    ON classroom_membership (classroom_id) WHERE end_date IS NULL;

CREATE TABLE teacher_assignment (
    id           UUID        PRIMARY KEY,
    teacher_id   UUID        NOT NULL REFERENCES teacher (user_id),
    classroom_id UUID        NOT NULL REFERENCES classroom (id),
    subject_id   UUID        NOT NULL REFERENCES subject (id),
    created_at   TIMESTAMPTZ NOT NULL,
    created_by   UUID,
    updated_at   TIMESTAMPTZ NOT NULL,
    updated_by   UUID,
    version      BIGINT      NOT NULL,
    CONSTRAINT uk_teacher_assignment UNIQUE (teacher_id, classroom_id, subject_id)
);

CREATE INDEX idx_teacher_assignment_classroom ON teacher_assignment (classroom_id);

-- Transactional outbox: rows are written in the business transaction and published to SNS later.
-- Payload contains personal data (names, contact info); published rows are deleted after the
-- retention period (KVKK).
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
