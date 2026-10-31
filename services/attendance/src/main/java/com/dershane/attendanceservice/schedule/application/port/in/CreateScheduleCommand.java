package com.dershane.attendanceservice.schedule.application.port.in;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record CreateScheduleCommand(
    UUID branchId,
    UUID classroomId,
    UUID teacherId,
    UUID subjectId,
    DayOfWeek dayOfWeek,
    LocalTime startsAt,
    LocalTime endsAt,
    LocalDate validFrom,
    LocalDate validTo) {}
