package com.dershane.userservice.enrollment.application.port.in;

import java.util.UUID;

public record AssignTeacherCommand(UUID classroomId, UUID subjectId) {}
