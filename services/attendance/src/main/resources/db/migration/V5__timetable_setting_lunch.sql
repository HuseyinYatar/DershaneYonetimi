-- Optional lunch break of the lesson-hour grid: the 1-based lesson hour that is lunch (0 = none). The
-- screen keeps that hour busy on every day; the bound mirrors TimetableSetting in the domain.
ALTER TABLE timetable_setting
    ADD COLUMN lunch_lesson INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_timetable_lunch CHECK (lunch_lesson >= 0 AND lunch_lesson <= lesson_count);
