package com.dershane.userservice.enrollment.application.port.in;

import java.util.UUID;

/** One teacher–classroom–subject assignment of a branch; carries ids and catalog names only. */
public record BranchAssignmentView(
    UUID assignmentId,
    UUID teacherId,
    UUID classroomId,
    String classroomName,
    UUID subjectId,
    String subjectName,
    int weeklyHours) {}
