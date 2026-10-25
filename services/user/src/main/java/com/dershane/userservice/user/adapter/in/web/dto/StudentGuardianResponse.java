package com.dershane.userservice.user.adapter.in.web.dto;

import com.dershane.userservice.user.domain.Relationship;
import java.util.UUID;

/** Carries personal data: {@code toString} prints identifiers only. */
public record StudentGuardianResponse(
    UUID guardianId,
    UUID studentId,
    String firstName,
    String lastName,
    String email,
    String phone,
    Relationship relationship) {

  @Override
  public String toString() {
    return "StudentGuardianResponse[guardianId=" + guardianId + ", studentId=" + studentId + "]";
  }
}
