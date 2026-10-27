package com.dershane.userservice.enrollment.adapter.in.web.dto;

import java.time.LocalDate;
import java.util.UUID;

/** Carries personal data: {@code toString} prints identifiers only. */
public record ClassroomStudentResponse(
    UUID studentId,
    String firstName,
    String lastName,
    String studentNumber,
    UUID membershipId,
    LocalDate startDate) {

  @Override
  public String toString() {
    return "ClassroomStudentResponse[studentId="
        + studentId
        + ", membershipId="
        + membershipId
        + "]";
  }
}
