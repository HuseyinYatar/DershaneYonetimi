package com.dershane.userservice.classroom.application.port.in;

import java.time.Instant;
import java.util.UUID;

/**
 * @param activeStudentCount null in lists, where counting every classroom would be wasteful
 */
public record ClassroomView(
    UUID id,
    UUID branchId,
    String name,
    int gradeLevel,
    String term,
    boolean active,
    Long activeStudentCount,
    Instant createdAt,
    Instant updatedAt) {}
