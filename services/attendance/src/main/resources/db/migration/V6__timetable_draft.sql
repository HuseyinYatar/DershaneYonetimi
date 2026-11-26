-- The automatic distribution proposed for a branch, kept until an admin saves it as class_schedule rows
-- or discards it. One draft per branch; its lessons are not schedules (no validity dates, no sessions).
CREATE TABLE timetable_draft (
    id         UUID        PRIMARY KEY,
    branch_id  UUID        NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID,
    version    BIGINT      NOT NULL
);

CREATE TABLE timetable_draft_lesson (
    id           UUID        PRIMARY KEY,
    draft_id     UUID        NOT NULL REFERENCES timetable_draft (id) ON DELETE CASCADE,
    classroom_id UUID        NOT NULL,
    teacher_id   UUID        NOT NULL,
    subject_id   UUID        NOT NULL,
    day_of_week  VARCHAR(9)  NOT NULL CHECK (day_of_week IN
                     ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')),
    starts_at    TIME        NOT NULL,
    ends_at      TIME        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    created_by   UUID,
    updated_at   TIMESTAMPTZ NOT NULL,
    updated_by   UUID,
    version      BIGINT      NOT NULL,
    CONSTRAINT ck_draft_lesson_times CHECK (ends_at > starts_at)
);

CREATE INDEX idx_timetable_draft_lesson_draft ON timetable_draft_lesson (draft_id);

-- Hours of a demand the planner could not place, shown next to the draft.
CREATE TABLE timetable_draft_missing (
    id           UUID        PRIMARY KEY,
    draft_id     UUID        NOT NULL REFERENCES timetable_draft (id) ON DELETE CASCADE,
    classroom_id UUID        NOT NULL,
    teacher_id   UUID        NOT NULL,
    subject_id   UUID        NOT NULL,
    missing_hours INTEGER    NOT NULL CHECK (missing_hours > 0),
    created_at   TIMESTAMPTZ NOT NULL,
    created_by   UUID,
    updated_at   TIMESTAMPTZ NOT NULL,
    updated_by   UUID,
    version      BIGINT      NOT NULL
);

CREATE INDEX idx_timetable_draft_missing_draft ON timetable_draft_missing (draft_id);
