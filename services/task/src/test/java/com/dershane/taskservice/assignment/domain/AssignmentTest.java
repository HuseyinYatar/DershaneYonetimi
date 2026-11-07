package com.dershane.taskservice.assignment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.dershane.taskservice.shared.domain.DomainException;
import com.dershane.taskservice.shared.domain.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssignmentTest {

  private static final Instant ASSIGNED_AT = Instant.parse("2026-11-09T06:00:00Z");
  private static final Instant DUE_AT = Instant.parse("2026-11-09T17:00:00Z");
  private static final Instant BEFORE_DUE = Instant.parse("2026-11-09T10:00:00Z");
  private static final Instant AFTER_DUE = Instant.parse("2026-11-09T18:00:00Z");
  private static final Duration LEAD = Duration.ofHours(6);

  private final UUID teacherId = UUID.randomUUID();

  private Assignment assignment() {
    return Assignment.assign(
        UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 11, 9), DUE_AT, ASSIGNED_AT);
  }

  private static void assertRejected(Runnable action, ErrorCode expected) {
    assertThatExceptionOfType(DomainException.class)
        .isThrownBy(action::run)
        .satisfies(ex -> assertThat(ex.getErrorCode()).isEqualTo(expected));
  }

  @Test
  void aNewAssignmentIsAssignedAndNotReminded() {
    Assignment assignment = assignment();

    assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
    assertThat(assignment.isReminderSent()).isFalse();
    assertThat(assignment.getSubmission()).isNull();
  }

  @Test
  void submittingBeforeTheDueTimeIsOnTime() {
    Assignment assignment = assignment();

    assignment.submit("  My answer ", List.of(), BEFORE_DUE);

    assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.SUBMITTED);
    assertThat(assignment.getSubmission().description()).isEqualTo("My answer");
    assertThat(assignment.getSubmission().late()).isFalse();
  }

  @Test
  void aLateSubmissionIsAcceptedAndMarkedLate() {
    Assignment assignment = assignment();
    assignment.markOverdue(AFTER_DUE);

    assignment.submit(null, List.of(UUID.randomUUID()), AFTER_DUE);

    assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.SUBMITTED);
    assertThat(assignment.getSubmission().late()).isTrue();
  }

  @Test
  void submittingAgainReplacesTheEarlierSubmission() {
    Assignment assignment = assignment();
    assignment.submit("first", List.of(), BEFORE_DUE);

    assignment.submit("second", List.of(), AFTER_DUE);

    assertThat(assignment.getSubmission().description()).isEqualTo("second");
    assertThat(assignment.getSubmission().late()).isTrue();
  }

  @Test
  void aSubmissionNeedsTextOrFiles() {
    assertRejected(
        () -> assignment().submit("  ", List.of(), BEFORE_DUE), ErrorCode.EMPTY_SUBMISSION);
  }

  @Test
  void aGradedOrCancelledAssignmentCannotBeSubmitted() {
    Assignment graded = assignment();
    graded.submit("answer", List.of(), BEFORE_DUE);
    graded.evaluate(teacherId, 90, null, BEFORE_DUE);
    Assignment cancelled = assignment();
    cancelled.cancelIfOpen(BEFORE_DUE);

    assertRejected(
        () -> graded.submit("again", List.of(), BEFORE_DUE), ErrorCode.ASSIGNMENT_ALREADY_GRADED);
    assertRejected(
        () -> cancelled.submit("x", List.of(), BEFORE_DUE), ErrorCode.ASSIGNMENT_CANCELLED);
  }

  @Test
  void evaluatingNeedsASubmission() {
    assertRejected(
        () -> assignment().evaluate(teacherId, 50, null, BEFORE_DUE), ErrorCode.NOT_SUBMITTED);
  }

  @Test
  void evaluatingGradesAndMayBeRepeated() {
    Assignment assignment = assignment();
    assignment.submit("answer", List.of(), BEFORE_DUE);

    assignment.evaluate(teacherId, 70, "OK", BEFORE_DUE);
    assignment.evaluate(teacherId, null, "Much better", AFTER_DUE);

    assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.GRADED);
    assertThat(assignment.getEvaluation().score()).isNull();
    assertThat(assignment.getEvaluation().feedback()).isEqualTo("Much better");
  }

  @Test
  void anEvaluationNeedsAScoreOrFeedbackAndAValidScore() {
    Assignment assignment = assignment();
    assignment.submit("answer", List.of(), BEFORE_DUE);

    assertRejected(
        () -> assignment.evaluate(teacherId, null, " ", BEFORE_DUE), ErrorCode.EMPTY_EVALUATION);
    assertRejected(
        () -> assignment.evaluate(teacherId, 101, null, BEFORE_DUE), ErrorCode.INVALID_SCORE);
    assertRejected(
        () -> assignment.evaluate(teacherId, -1, null, BEFORE_DUE), ErrorCode.INVALID_SCORE);
    assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.SUBMITTED);
  }

  @Test
  void onlyAnOpenAssignmentBecomesOverdueAfterItsDueTime() {
    Assignment open = assignment();
    Assignment submitted = assignment();
    submitted.submit("answer", List.of(), BEFORE_DUE);

    assertThat(open.markOverdue(BEFORE_DUE)).isFalse();
    assertThat(open.markOverdue(AFTER_DUE)).isTrue();
    assertThat(open.getStatus()).isEqualTo(AssignmentStatus.OVERDUE);
    assertThat(open.markOverdue(AFTER_DUE)).isFalse();
    assertThat(submitted.markOverdue(AFTER_DUE)).isFalse();
  }

  @Test
  void aReminderIsDueOnceInsideTheWindowForAssignmentsHandedOutBeforeIt() {
    Assignment assignment = assignment();

    assertThat(assignment.reminderDue(Instant.parse("2026-11-09T10:59:59Z"), LEAD)).isFalse();
    assertThat(assignment.reminderDue(Instant.parse("2026-11-09T11:00:00Z"), LEAD)).isTrue();
    assertThat(assignment.reminderDue(DUE_AT, LEAD)).isFalse();

    assignment.markReminderSent();

    assertThat(assignment.reminderDue(BEFORE_DUE.plusSeconds(3600), LEAD)).isFalse();
  }

  @Test
  void aFreshAssignmentInsideTheWindowIsNotRemindedBecauseItWasJustAnnounced() {
    Assignment fresh =
        Assignment.assign(
            UUID.randomUUID(),
            UUID.randomUUID(),
            LocalDate.of(2026, 11, 9),
            DUE_AT,
            Instant.parse("2026-11-09T12:00:00Z"));

    assertThat(fresh.reminderDue(Instant.parse("2026-11-09T13:00:00Z"), LEAD)).isFalse();
  }

  @Test
  void cancellingOnlyAffectsOpenAssignmentsBeforeTheirDueTime() {
    Assignment open = assignment();
    Assignment overdue = assignment();
    overdue.markOverdue(AFTER_DUE);
    Assignment submitted = assignment();
    submitted.submit("answer", List.of(), BEFORE_DUE);

    assertThat(open.cancelIfOpen(BEFORE_DUE)).isTrue();
    assertThat(open.getStatus()).isEqualTo(AssignmentStatus.CANCELLED);
    assertThat(overdue.cancelIfOpen(AFTER_DUE)).isFalse();
    assertThat(submitted.cancelIfOpen(BEFORE_DUE)).isFalse();
  }
}
