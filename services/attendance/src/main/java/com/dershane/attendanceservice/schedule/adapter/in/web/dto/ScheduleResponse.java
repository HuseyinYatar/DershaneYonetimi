package com.dershane.attendanceservice.schedule.adapter.in.web.dto;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record ScheduleResponse(
    UUID id,
    UUID branchId,
    UUID classroomId,
    UUID teacherId,
    UUID subjectId,
    DayOfWeek dayOfWeek,
    LocalTime startsAt,
    LocalTime endsAt,
    LocalDate validFrom,
    LocalDate validTo,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {}
