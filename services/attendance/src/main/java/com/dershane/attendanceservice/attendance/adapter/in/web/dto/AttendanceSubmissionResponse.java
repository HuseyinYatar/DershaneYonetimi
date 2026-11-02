package com.dershane.attendanceservice.attendance.adapter.in.web.dto;

import java.time.Instant;
import java.util.UUID;

public record AttendanceSubmissionResponse(
    UUID sessionId, Instant takenAt, int present, int absent, int late, int excused) {}
