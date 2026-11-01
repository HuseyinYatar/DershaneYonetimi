package com.dershane.attendanceservice.session.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ReassignTeacherRequest(@NotNull UUID teacherId) {}
