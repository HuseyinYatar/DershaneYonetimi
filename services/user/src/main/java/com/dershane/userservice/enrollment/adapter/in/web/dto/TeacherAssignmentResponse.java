package com.dershane.userservice.enrollment.adapter.in.web.dto;

import java.time.Instant;
import java.util.UUID;

public record TeacherAssignmentResponse(
    UUID id,
    UUID teacherId,
    UUID classroomId,
    String classroomName,
    UUID subjectId,
    String subjectName,
    Instant createdAt) {}
