package com.dershane.attendanceservice.session.application.port.in;

import com.dershane.attendanceservice.session.domain.SessionStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record SessionView(
    UUID id,
    UUID scheduleId,
    UUID branchId,
    UUID classroomId,
    UUID teacherId,
    UUID subjectId,
    LocalDate sessionDate,
    LocalTime startsAt,
    LocalTime endsAt,
    SessionStatus status,
    Instant takenAt,
    UUID takenBy,
    Instant createdAt,
    Instant updatedAt) {}
