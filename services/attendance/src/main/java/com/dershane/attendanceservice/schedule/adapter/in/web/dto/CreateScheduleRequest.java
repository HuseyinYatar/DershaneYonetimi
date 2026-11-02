package com.dershane.attendanceservice.schedule.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record CreateScheduleRequest(
    @NotNull UUID branchId,
    @NotNull UUID classroomId,
    @NotNull UUID teacherId,
    @NotNull UUID subjectId,
    @NotNull DayOfWeek dayOfWeek,
    @NotNull LocalTime startsAt,
    @NotNull LocalTime endsAt,
    @NotNull LocalDate validFrom,
    LocalDate validTo) {}
