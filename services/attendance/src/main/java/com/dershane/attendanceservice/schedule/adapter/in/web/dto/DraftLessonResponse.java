package com.dershane.attendanceservice.schedule.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

/** Hours are written as {@code HH:mm}. */
public record DraftLessonResponse(
    UUID id,
    UUID classroomId,
    UUID teacherId,
    UUID subjectId,
    DayOfWeek dayOfWeek,
    @JsonFormat(pattern = "HH:mm") LocalTime startsAt,
    @JsonFormat(pattern = "HH:mm") LocalTime endsAt) {}
