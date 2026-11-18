package com.dershane.attendanceservice.schedule.adapter.in.web.dto;

import java.util.UUID;

/** Hours of a classroom's subject that the automatic distribution could not place. */
public record DraftMissingResponse(
    UUID classroomId, UUID teacherId, UUID subjectId, int missingHours) {}
