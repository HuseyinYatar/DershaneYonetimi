package com.dershane.attendanceservice.roster.application.port.in;

import java.util.UUID;

/** Personal data (student number, name): masked in {@code toString}. */
public record RosterEntryView(UUID studentId, String studentNumber, String fullName) {

  @Override
  public String toString() {
    return "RosterEntryView[studentId=" + studentId + "]";
  }
}
