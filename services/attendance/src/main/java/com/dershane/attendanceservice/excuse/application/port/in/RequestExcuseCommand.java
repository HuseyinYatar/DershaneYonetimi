package com.dershane.attendanceservice.excuse.application.port.in;

import java.time.LocalDate;
import java.util.UUID;

/** The reason is free text about a child: masked in {@code toString}. */
public record RequestExcuseCommand(
    UUID studentId, LocalDate fromDate, LocalDate toDate, String reason) {

  @Override
  public String toString() {
    return "RequestExcuseCommand[studentId=" + studentId + "]";
  }
}
