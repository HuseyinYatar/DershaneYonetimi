package com.dershane.attendanceservice.attendance.adapter.in.web.dto;

import com.dershane.attendanceservice.attendance.domain.AttendanceStatus;
import java.util.UUID;

/** Student number and name are personal data: masked in {@code toString}. */
public record RosterRowResponse(
    UUID studentId,
    String studentNumber,
    String fullName,
    AttendanceStatus status,
    boolean excused,
    UUID excuseId) {

  @Override
  public String toString() {
    return "RosterRowResponse[studentId=" + studentId + "]";
  }
}
