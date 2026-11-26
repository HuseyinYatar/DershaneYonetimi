package com.dershane.userservice.enrollment.application.port.in;

import java.time.Instant;
import java.util.UUID;

/** A classroom as the caller's own classrooms list shows it. */
public record ClassroomSummaryView(
    UUID id,
    UUID branchId,
    String name,
    int gradeLevel,
    String term,
    boolean active,
    Long activeStudentCount,
    Instant createdAt,
    Instant updatedAt) {}
