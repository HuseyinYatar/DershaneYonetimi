package com.dershane.userservice.enrollment.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * A classroom of the caller. It has the same fields as the classroom endpoints' response and is
 * documented under the same schema name; {@code activeStudentCount} is always null here, as it is
 * in lists.
 */
@Schema(name = "ClassroomResponse")
public record MyClassroomResponse(
    UUID id,
    UUID branchId,
    String name,
    int gradeLevel,
    String term,
    boolean active,
    Long activeStudentCount,
    Instant createdAt,
    Instant updatedAt) {}
