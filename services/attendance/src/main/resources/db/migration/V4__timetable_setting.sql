-- Lesson-hour grid of a branch (one row per branch). It only lays out the timetable screen; the
-- times of class_schedule rows stay free. The bounds mirror TimetableSetting in the domain.
CREATE TABLE timetable_setting (
    branch_id         UUID        PRIMARY KEY,
    first_lesson_start TIME       NOT NULL,
    lesson_minutes    INTEGER     NOT NULL,
    break_minutes     INTEGER     NOT NULL,
    lesson_count      INTEGER     NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL,
    created_by        UUID,
    updated_at        TIMESTAMPTZ NOT NULL,
    updated_by        UUID,
    version           BIGINT      NOT NULL,
    CONSTRAINT ck_timetable_first_start CHECK (first_lesson_start BETWEEN TIME '05:00' AND TIME '20:00'),
    CONSTRAINT ck_timetable_lesson_minutes CHECK (lesson_minutes BETWEEN 20 AND 120),
    CONSTRAINT ck_timetable_break_minutes CHECK (break_minutes BETWEEN 0 AND 60),
    CONSTRAINT ck_timetable_lesson_count CHECK (lesson_count BETWEEN 1 AND 16)
);
