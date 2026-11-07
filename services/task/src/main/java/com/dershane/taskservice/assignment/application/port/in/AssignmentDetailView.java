package com.dershane.taskservice.assignment.application.port.in;

import com.dershane.taskservice.assignment.domain.AssignmentStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** One assignment with its task's texts, the submission and the evaluation. Texts are masked. */
public record AssignmentDetailView(
    UUID id,
    UUID taskId,
    String taskTitle,
    String taskDescription,
    List<UUID> taskAttachmentFileIds,
    UUID teacherId,
    UUID branchId,
    UUID studentId,
    LocalDate occurrenceDate,
    Instant dueAt,
    AssignmentStatus status,
    Instant assignedAt,
    SubmissionView submission,
    EvaluationView evaluation) {

  @Override
  public String toString() {
    return "AssignmentDetailView[id=" + id + "]";
  }
}
