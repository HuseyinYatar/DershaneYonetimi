package com.dershane.userservice.enrollment.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateTeacherAssignmentRequest(@NotNull Integer weeklyHours) {}
