package com.dershane.userservice.enrollment.application.port.in;

import java.time.Instant;
import java.util.UUID;

public record TeacherAssignmentView(
    UUID id,
    UUID teacherId,
    UUID classroomId,
    String classroomName,
    UUID subjectId,
    String subjectName,
    Instant createdAt) {}
