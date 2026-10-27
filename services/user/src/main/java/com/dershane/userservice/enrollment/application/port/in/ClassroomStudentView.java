package com.dershane.userservice.enrollment.application.port.in;

import java.time.LocalDate;
import java.util.UUID;

/** Carries personal data: {@code toString} prints identifiers only. */
public record ClassroomStudentView(
    UUID studentId,
    String firstName,
    String lastName,
    String studentNumber,
    UUID membershipId,
    LocalDate startDate) {

  @Override
  public String toString() {
    return "ClassroomStudentView[studentId=" + studentId + ", membershipId=" + membershipId + "]";
  }
}
