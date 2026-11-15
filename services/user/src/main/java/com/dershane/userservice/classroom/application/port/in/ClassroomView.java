package com.dershane.userservice.classroom.application.port.in;

import java.time.Instant;
import java.util.UUID;

/**
 * @param activeStudentCount filled by the service; the persistence mapper leaves it null
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
