package com.dershane.taskservice.assignment.adapter.in.web.dto;

import com.dershane.taskservice.assignment.domain.AssignmentStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Carries free texts of the task, the submission and the evaluation: masked in {@code toString}.
 */
public record AssignmentDetailResponse(
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
    SubmissionResponse submission,
    EvaluationResponse evaluation) {

  @Override
  public String toString() {
    return "AssignmentDetailResponse[id=" + id + "]";
  }
}
