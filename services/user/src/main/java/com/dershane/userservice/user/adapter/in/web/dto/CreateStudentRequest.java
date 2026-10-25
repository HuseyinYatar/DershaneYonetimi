package com.dershane.userservice.user.adapter.in.web.dto;

import com.dershane.userservice.user.domain.TargetExam;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Carries personal data: {@code toString} prints identifiers only. */
public record CreateStudentRequest(
    @NotNull UUID branchId,
    @NotBlank @Size(max = 80) String firstName,
    @NotBlank @Size(max = 80) String lastName,
    @Email @Size(max = 254) String email,
    @Pattern(regexp = UserPatterns.PHONE) String phone,
    @NotBlank @Pattern(regexp = UserPatterns.STUDENT_NUMBER) String studentNumber,
    @NotNull @Min(1) @Max(13) Integer gradeLevel,
    @NotNull TargetExam targetExam) {

  @Override
  public String toString() {
    return "CreateStudentRequest[branchId=" + branchId + "]";
  }
}
