package com.dershane.userservice.classroom.adapter.in.web.dto;

import java.time.Instant;
import java.util.UUID;

public record ClassroomResponse(
    UUID id,
    UUID branchId,
    String name,
    int gradeLevel,
    String term,
    boolean active,
    Long activeStudentCount,
    Instant createdAt,
    Instant updatedAt) {}
