package com.dershane.userservice.user.application.port.in;

import com.dershane.userservice.user.domain.TargetExam;
import java.util.UUID;

/** Carries personal data: {@code toString} prints identifiers only. */
public record RegisterStudentCommand(
    UUID branchId,
    String firstName,
    String lastName,
    String email,
    String phone,
    String studentNumber,
    int gradeLevel,
    TargetExam targetExam) {

  @Override
  public String toString() {
    return "RegisterStudentCommand[branchId=" + branchId + "]";
  }
}
