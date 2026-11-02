package com.dershane.attendanceservice.attendance.application.port.in;

import com.dershane.attendanceservice.attendance.domain.AttendanceStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** The note is free text from the teacher: masked in {@code toString}. */
public record AttendanceRecordView(
    UUID id,
    UUID sessionId,
    UUID studentId,
    UUID classroomId,
    UUID subjectId,
    LocalDate sessionDate,
    LocalTime startsAt,
    LocalTime endsAt,
    AttendanceStatus status,
    UUID excuseId,
    String note,
    Instant markedAt,
    UUID markedBy) {

  @Override
  public String toString() {
    return "AttendanceRecordView[id=" + id + "]";
  }
}
