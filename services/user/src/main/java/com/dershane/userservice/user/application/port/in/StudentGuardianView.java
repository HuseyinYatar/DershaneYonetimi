package com.dershane.userservice.user.application.port.in;

import com.dershane.userservice.user.domain.Relationship;
import java.util.UUID;

/** Carries personal data: {@code toString} prints identifiers only. */
public record StudentGuardianView(
    UUID guardianId,
    UUID studentId,
    String firstName,
    String lastName,
    String email,
    String phone,
    Relationship relationship) {

  @Override
  public String toString() {
    return "StudentGuardianView[guardianId=" + guardianId + ", studentId=" + studentId + "]";
  }
}
