package com.dershane.attendanceservice.schedule.application.port.in;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** A branch's draft timetable: the proposed lessons and the hours that could not be placed. */
public record TimetableDraftView(
    UUID id, UUID branchId, Instant createdAt, List<Lesson> lessons, List<Missing> missing) {

  public record Lesson(
      UUID id,
      UUID classroomId,
      UUID teacherId,
      UUID subjectId,
      DayOfWeek dayOfWeek,
      LocalTime startsAt,
      LocalTime endsAt) {}

  public record Missing(UUID classroomId, UUID teacherId, UUID subjectId, int missingHours) {}
}
