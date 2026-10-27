package com.dershane.userservice.user.adapter.in.web.dto;

import com.dershane.userservice.user.domain.TargetExam;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Null fields are left unchanged. Carries personal data: {@code toString} is masked. */
public record UpdateStudentRequest(
    @Size(min = 1, max = 80) String firstName,
    @Size(min = 1, max = 80) String lastName,
    @Email @Size(max = 254) String email,
    @Pattern(regexp = UserPatterns.PHONE) String phone,
    @Pattern(regexp = UserPatterns.STUDENT_NUMBER) String studentNumber,
    @Min(1) @Max(13) Integer gradeLevel,
    TargetExam targetExam) {

  @Override
  public String toString() {
    return "UpdateStudentRequest[masked]";
  }
}
