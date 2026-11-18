package com.dershane.userservice.enrollment.adapter.in.web.dto;

import java.util.UUID;

public record BranchTeacherAssignmentResponse(
    UUID assignmentId,
    UUID teacherId,
    UUID classroomId,
    String classroomName,
    UUID subjectId,
    String subjectName,
    int weeklyHours) {}
