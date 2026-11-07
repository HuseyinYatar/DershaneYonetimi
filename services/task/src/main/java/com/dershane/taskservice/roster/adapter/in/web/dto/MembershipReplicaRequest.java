package com.dershane.taskservice.roster.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/** Personal data (student number, name): masked in {@code toString}. */
public record MembershipReplicaRequest(
    @NotNull UUID studentId,
    @NotNull UUID classroomId,
    @NotNull UUID branchId,
    @NotBlank @Size(max = 32) String studentNumber,
    @NotBlank @Size(max = 200) String fullName,
    @NotNull LocalDate startDate) {

  @Override
  public String toString() {
    return "MembershipReplicaRequest[studentId=" + studentId + "]";
  }
}
