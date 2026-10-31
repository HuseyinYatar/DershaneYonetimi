package com.dershane.attendanceservice.attendance.adapter.in.web.dto;

import com.dershane.attendanceservice.attendance.domain.AttendanceStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The reason is free text: masked in {@code toString}. */
public record CorrectRecordRequest(
    @NotNull AttendanceStatus status, @NotBlank @Size(max = 500) String reason) {

  @Override
  public String toString() {
    return "CorrectRecordRequest[status=" + status + "]";
  }
}
