package com.dershane.attendanceservice.excuse.adapter.in.web.dto;

import com.dershane.attendanceservice.excuse.domain.ExcuseStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** The reason and the decision note are free text about a child: masked in {@code toString}. */
public record ExcuseResponse(
    UUID id,
    UUID studentId,
    UUID branchId,
    LocalDate fromDate,
    LocalDate toDate,
    String reason,
    ExcuseStatus status,
    UUID requestedBy,
    UUID decidedBy,
    Instant decidedAt,
    String decisionNote,
    Instant createdAt) {

  @Override
  public String toString() {
    return "ExcuseResponse[id=" + id + "]";
  }
}
