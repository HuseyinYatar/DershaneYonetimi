package com.dershane.userservice.enrollment.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/** {@code startDate} defaults to today (Europe/Istanbul). */
public record AssignClassroomRequest(@NotNull UUID classroomId, LocalDate startDate) {}
