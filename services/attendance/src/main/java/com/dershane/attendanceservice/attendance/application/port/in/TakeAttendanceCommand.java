package com.dershane.attendanceservice.attendance.application.port.in;

import com.dershane.attendanceservice.attendance.domain.AttendanceStatus;
import java.util.List;
import java.util.UUID;

/**
 * The full list of a session.
 *
 * @param reason required only when an admin takes attendance after the session day ended
 */
public record TakeAttendanceCommand(UUID sessionId, List<Entry> entries, String reason) {

  /** The note is optional free text: masked in {@code toString}. */
  public record Entry(UUID studentId, AttendanceStatus status, String note) {

    @Override
    public String toString() {
      return "Entry[studentId=" + studentId + "]";
    }
  }

  @Override
  public String toString() {
    return "TakeAttendanceCommand[sessionId=" + sessionId + ", entries=" + entries.size() + "]";
  }
}
