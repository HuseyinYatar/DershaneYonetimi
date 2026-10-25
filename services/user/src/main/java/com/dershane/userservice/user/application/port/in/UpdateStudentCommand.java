package com.dershane.userservice.user.application.port.in;

import com.dershane.userservice.user.domain.TargetExam;

/** Null fields are left unchanged. Carries personal data: {@code toString} is masked. */
public record UpdateStudentCommand(
    String firstName,
    String lastName,
    String email,
    String phone,
    String studentNumber,
    Integer gradeLevel,
    TargetExam targetExam) {

  @Override
  public String toString() {
    return "UpdateStudentCommand[masked]";
  }
}
