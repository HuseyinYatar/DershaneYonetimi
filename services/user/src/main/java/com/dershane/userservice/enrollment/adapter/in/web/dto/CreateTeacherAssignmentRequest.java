package com.dershane.userservice.enrollment.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateTeacherAssignmentRequest(@NotNull UUID classroomId, @NotNull UUID subjectId) {}
