package com.dershane.taskservice.assignment.application.port.in;

import com.dershane.taskservice.assignment.domain.AssignmentStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A row of an assignment list; the submission and evaluation come with the detail view. */
public record AssignmentSummaryView(
    UUID id,
    UUID taskId,
    String taskTitle,
    UUID studentId,
    LocalDate occurrenceDate,
    Instant dueAt,
    AssignmentStatus status) {

  @Override
  public String toString() {
    return "AssignmentSummaryView[id=" + id + "]";
  }
}
