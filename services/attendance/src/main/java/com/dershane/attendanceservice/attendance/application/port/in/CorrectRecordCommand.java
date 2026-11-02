package com.dershane.attendanceservice.attendance.application.port.in;

import com.dershane.attendanceservice.attendance.domain.AttendanceStatus;
import java.util.UUID;

/** The reason is free text: masked in {@code toString}. */
public record CorrectRecordCommand(
    UUID sessionId, UUID studentId, AttendanceStatus status, String reason) {

  @Override
  public String toString() {
    return "CorrectRecordCommand[sessionId=" + sessionId + ", studentId=" + studentId + "]";
  }
}
