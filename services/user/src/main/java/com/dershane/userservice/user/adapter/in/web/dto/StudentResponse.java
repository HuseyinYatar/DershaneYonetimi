package com.dershane.userservice.user.adapter.in.web.dto;

import com.dershane.userservice.user.domain.TargetExam;
import java.time.Instant;
import java.util.UUID;

/** Carries personal data: {@code toString} prints identifiers only. */
public record StudentResponse(
    UUID id,
    UUID branchId,
    String firstName,
    String lastName,
    String email,
    String phone,
    String username,
    boolean active,
    String studentNumber,
    int gradeLevel,
    TargetExam targetExam,
    Instant createdAt,
    Instant updatedAt) {

  @Override
  public String toString() {
    return "StudentResponse[id=" + id + "]";
  }
}
