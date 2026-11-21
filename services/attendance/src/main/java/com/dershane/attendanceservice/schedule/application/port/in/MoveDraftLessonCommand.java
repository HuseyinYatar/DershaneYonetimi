package com.dershane.attendanceservice.schedule.application.port.in;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

public record MoveDraftLessonCommand(
    UUID branchId, UUID lessonId, DayOfWeek dayOfWeek, LocalTime startsAt, LocalTime endsAt) {}
