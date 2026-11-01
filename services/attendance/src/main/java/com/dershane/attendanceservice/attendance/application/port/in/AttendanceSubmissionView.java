package com.dershane.attendanceservice.attendance.application.port.in;

import java.time.Instant;
import java.util.UUID;

/** What a submission came to: the counts per final status. */
public record AttendanceSubmissionView(
    UUID sessionId, Instant takenAt, int present, int absent, int late, int excused) {}
