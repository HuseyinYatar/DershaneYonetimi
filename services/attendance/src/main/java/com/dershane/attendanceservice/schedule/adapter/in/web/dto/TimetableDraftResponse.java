package com.dershane.attendanceservice.schedule.adapter.in.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TimetableDraftResponse(
    UUID id,
    UUID branchId,
    Instant createdAt,
    List<DraftLessonResponse> lessons,
    List<DraftMissingResponse> missing) {}
