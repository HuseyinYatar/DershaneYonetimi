package com.dershane.attendanceservice.attendance.adapter.in.web.dto;

import com.dershane.attendanceservice.attendance.domain.AttendanceStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** The note is optional free text from the teacher: masked in {@code toString}. */
public record AttendanceEntryRequest(
    @NotNull UUID studentId, @NotNull AttendanceStatus status, @Size(max = 200) String note) {

  @Override
  public String toString() {
    return "AttendanceEntryRequest[studentId=" + studentId + "]";
  }
}
