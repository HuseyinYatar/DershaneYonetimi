package com.dershane.attendanceservice.session.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record CreateSessionRequest(
    @NotNull UUID branchId,
    @NotNull UUID classroomId,
    @NotNull UUID teacherId,
    @NotNull UUID subjectId,
    @NotNull LocalDate sessionDate,
    @NotNull LocalTime startsAt,
    @NotNull LocalTime endsAt) {}
