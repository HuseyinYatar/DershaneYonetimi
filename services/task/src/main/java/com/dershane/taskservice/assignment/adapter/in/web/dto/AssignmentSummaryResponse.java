package com.dershane.taskservice.assignment.adapter.in.web.dto;

import com.dershane.taskservice.assignment.domain.AssignmentStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AssignmentSummaryResponse(
    UUID id,
    UUID taskId,
    String taskTitle,
    UUID studentId,
    LocalDate occurrenceDate,
    Instant dueAt,
    AssignmentStatus status) {

  @Override
  public String toString() {
    return "AssignmentSummaryResponse[id=" + id + "]";
  }
}
