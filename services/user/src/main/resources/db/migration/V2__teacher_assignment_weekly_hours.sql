-- Weekly lesson hours the classroom needs for this subject (0 = not planned yet).
ALTER TABLE teacher_assignment
    ADD COLUMN weekly_hours INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_teacher_assignment_weekly_hours CHECK (weekly_hours BETWEEN 0 AND 40);
