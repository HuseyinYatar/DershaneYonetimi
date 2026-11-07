package com.dershane.taskservice.assignment.domain;

import com.dershane.taskservice.shared.domain.ErrorCode;
import com.dershane.taskservice.shared.domain.Rejections;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * One task handed to one student for one occurrence (diagram: GorevAtamasi). The submission and the
 * evaluation are parts of it: they change only through the methods here, so the status and the two
 * always agree.
 */
@Slf4j
@Getter
public class Assignment {

  private static final int MAX_SCORE = 100;

  /** Null until the assignment is saved for the first time. */
  private final UUID id;

  /** Version the assignment was loaded with; null until saved. Used for optimistic locking. */
  private final Long version;

  private final UUID taskId;
  private final UUID studentId;
  private final LocalDate occurrenceDate;
  private final Instant dueAt;
  private AssignmentStatus status;
  private boolean reminderSent;
  private final Instant assignedAt;
  private Submission submission;
  private Evaluation evaluation;

  /** Rehydrates a saved assignment. Use {@link #assign} for a new one. */
  // A rehydrating constructor takes every field of the aggregate.
  @SuppressWarnings("checkstyle:ParameterNumber")
  public Assignment(
      UUID id,
      Long version,
      UUID taskId,
      UUID studentId,
      LocalDate occurrenceDate,
      Instant dueAt,
      AssignmentStatus status,
      boolean reminderSent,
      Instant assignedAt,
      Submission submission,
      Evaluation evaluation) {
    this.id = id;
    this.version = version;
    this.taskId = taskId;
    this.studentId = studentId;
    this.occurrenceDate = occurrenceDate;
    this.dueAt = dueAt;
    this.status = status;
    this.reminderSent = reminderSent;
    this.assignedAt = assignedAt;
    this.submission = submission;
    this.evaluation = evaluation;
  }

  public static Assignment assign(
      UUID taskId, UUID studentId, LocalDate occurrenceDate, Instant dueAt, Instant now) {
    return new Assignment(
        null,
        null,
        taskId,
        studentId,
        occurrenceDate,
        dueAt,
        AssignmentStatus.ASSIGNED,
        false,
        now,
        null,
        null);
  }

  /**
   * Hands in (or replaces) the submission. A late submission is accepted and marked; handing in
   * again replaces the earlier one until the teacher evaluates.
   */
  public void submit(String description, List<UUID> fileIds, Instant now) {
    requireNotClosed("Submission rejected");
    String text = description == null || description.isBlank() ? null : description.strip();
    if (text == null && fileIds.isEmpty()) {
      throw Rejections.reject(
          log,
          ErrorCode.EMPTY_SUBMISSION,
          "Submission rejected: neither a description nor a file",
          "assignmentId",
          id);
    }
    this.submission = new Submission(text, List.copyOf(fileIds), now, now.isAfter(dueAt));
    this.status = AssignmentStatus.SUBMITTED;
  }

  /** Grades the submission; evaluating again overwrites the earlier verdict. */
  public void evaluate(UUID evaluatorId, Integer score, String feedback, Instant now) {
    if (status == AssignmentStatus.CANCELLED) {
      throw cancelled("Evaluation rejected");
    }
    if (submission == null) {
      throw Rejections.reject(
          log,
          ErrorCode.NOT_SUBMITTED,
          "Evaluation rejected: nothing was submitted",
          "assignmentId",
          id);
    }
    if (score != null && (score < 0 || score > MAX_SCORE)) {
      throw Rejections.reject(
          log,
          ErrorCode.INVALID_SCORE,
          "Evaluation rejected: the score is out of range",
          "assignmentId",
          id);
    }
    String text = feedback == null || feedback.isBlank() ? null : feedback.strip();
    if (score == null && text == null) {
      throw Rejections.reject(
          log,
          ErrorCode.EMPTY_EVALUATION,
          "Evaluation rejected: neither a score nor feedback",
          "assignmentId",
          id);
    }
    this.evaluation = new Evaluation(evaluatorId, score, text, now);
    this.status = AssignmentStatus.GRADED;
  }

  /**
   * Marks an unsubmitted assignment whose due time passed.
   *
   * @return false when nothing changed
   */
  public boolean markOverdue(Instant now) {
    if (status != AssignmentStatus.ASSIGNED || dueAt.isAfter(now)) {
      return false;
    }
    this.status = AssignmentStatus.OVERDUE;
    return true;
  }

  /**
   * Whether a reminder is due: still open, not reminded yet, inside the window before the due time
   * and handed out before that window opened (a fresh assignment was just announced by {@code
   * TaskAssigned}).
   */
  public boolean reminderDue(Instant now, Duration leadTime) {
    Instant windowStart = dueAt.minus(leadTime);
    return status == AssignmentStatus.ASSIGNED
        && !reminderSent
        && !now.isBefore(windowStart)
        && now.isBefore(dueAt)
        && !assignedAt.isAfter(windowStart);
  }

  public void markReminderSent() {
    this.reminderSent = true;
  }

  /**
   * Cancels an open assignment whose due time has not passed (the student left the classroom).
   * Overdue, submitted and graded ones stay as they are.
   *
   * @return false when nothing changed
   */
  public boolean cancelIfOpen(Instant now) {
    if (status != AssignmentStatus.ASSIGNED || !dueAt.isAfter(now)) {
      return false;
    }
    this.status = AssignmentStatus.CANCELLED;
    return true;
  }

  private void requireNotClosed(String what) {
    if (status == AssignmentStatus.GRADED) {
      throw Rejections.reject(
          log, ErrorCode.ASSIGNMENT_ALREADY_GRADED, what + ": already graded", "assignmentId", id);
    }
    if (status == AssignmentStatus.CANCELLED) {
      throw cancelled(what);
    }
  }

  private RuntimeException cancelled(String what) {
    return Rejections.reject(
        log,
        ErrorCode.ASSIGNMENT_CANCELLED,
        what + ": assignment was cancelled",
        "assignmentId",
        id);
  }

  @Override
  public String toString() {
    return "Assignment[id=" + id + "]";
  }
}
