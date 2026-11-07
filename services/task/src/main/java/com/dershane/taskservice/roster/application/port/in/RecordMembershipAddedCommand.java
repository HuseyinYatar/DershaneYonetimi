package com.dershane.taskservice.roster.application.port.in;

import java.time.LocalDate;
import java.util.UUID;

/** Personal data (student number, name): masked in {@code toString}. */
public record RecordMembershipAddedCommand(
    UUID membershipId,
    UUID studentId,
    UUID classroomId,
    UUID branchId,
    String studentNumber,
    String fullName,
    LocalDate startDate) {

  @Override
  public String toString() {
    return "RecordMembershipAddedCommand[membershipId=" + membershipId + "]";
  }
}
