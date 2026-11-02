package com.dershane.attendanceservice.excuse.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/** The reason is free text about a child: masked in {@code toString}. */
public record RequestExcuseRequest(
    @NotNull UUID studentId,
    @NotNull LocalDate fromDate,
    @NotNull LocalDate toDate,
    @NotBlank @Size(max = 500) String reason) {

  @Override
  public String toString() {
    return "RequestExcuseRequest[studentId=" + studentId + "]";
  }
}
