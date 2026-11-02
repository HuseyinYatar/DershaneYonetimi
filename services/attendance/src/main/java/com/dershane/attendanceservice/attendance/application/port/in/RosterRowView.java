package com.dershane.attendanceservice.attendance.application.port.in;

import com.dershane.attendanceservice.attendance.domain.AttendanceStatus;
import java.util.UUID;

/**
 * One student on a session's list.
 *
 * @param status null until attendance is taken
 * @param excused an approved excuse covers the session day, so the teacher knows beforehand
 * @param excuseId the approved excuse (the one applied once taken); null when there is none
 *     <p>Student number and name are personal data: masked in {@code toString}.
 */
public record RosterRowView(
    UUID studentId,
    String studentNumber,
    String fullName,
    AttendanceStatus status,
    boolean excused,
    UUID excuseId) {

  @Override
  public String toString() {
    return "RosterRowView[studentId=" + studentId + "]";
  }
}
