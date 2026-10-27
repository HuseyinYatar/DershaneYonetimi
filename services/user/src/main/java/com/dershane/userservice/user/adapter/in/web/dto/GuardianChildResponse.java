package com.dershane.userservice.user.adapter.in.web.dto;

import com.dershane.userservice.user.domain.Relationship;
import java.util.UUID;

/** Carries personal data: {@code toString} prints identifiers only. */
public record GuardianChildResponse(
    UUID studentId,
    String firstName,
    String lastName,
    String studentNumber,
    int gradeLevel,
    Relationship relationship,
    boolean active) {

  @Override
  public String toString() {
    return "GuardianChildResponse[studentId=" + studentId + "]";
  }
}
