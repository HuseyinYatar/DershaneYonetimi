package com.dershane.taskservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.dershane.taskservice.assignment.adapter.in.web.dto.AssignmentDetailResponse;
import com.dershane.taskservice.assignment.adapter.in.web.dto.EvaluateAssignmentRequest;
import com.dershane.taskservice.assignment.adapter.in.web.dto.EvaluationResponse;
import com.dershane.taskservice.assignment.adapter.in.web.dto.SubmissionResponse;
import com.dershane.taskservice.assignment.adapter.in.web.dto.SubmitAssignmentRequest;
import com.dershane.taskservice.assignment.application.port.in.AssignmentDetailView;
import com.dershane.taskservice.assignment.application.port.in.EvaluateAssignmentCommand;
import com.dershane.taskservice.assignment.application.port.in.EvaluationView;
import com.dershane.taskservice.assignment.application.port.in.SubmissionView;
import com.dershane.taskservice.assignment.application.port.in.SubmitAssignmentCommand;
import com.dershane.taskservice.assignment.domain.Assignment;
import com.dershane.taskservice.assignment.domain.AssignmentStatus;
import com.dershane.taskservice.assignment.domain.Evaluation;
import com.dershane.taskservice.assignment.domain.Submission;
import com.dershane.taskservice.roster.adapter.in.web.dto.MembershipReplicaRequest;
import com.dershane.taskservice.roster.application.port.in.RecordMembershipAddedCommand;
import com.dershane.taskservice.roster.application.port.in.RosterEntryView;
import com.dershane.taskservice.roster.domain.MembershipReplica;
import com.dershane.taskservice.task.adapter.in.web.dto.CreateTaskRequest;
import com.dershane.taskservice.task.adapter.in.web.dto.TaskResponse;
import com.dershane.taskservice.task.adapter.in.web.dto.UpdateTaskRequest;
import com.dershane.taskservice.task.application.port.in.CreateTaskCommand;
import com.dershane.taskservice.task.application.port.in.TaskView;
import com.dershane.taskservice.task.application.port.in.UpdateTaskCommand;
import com.dershane.taskservice.task.domain.Recurrence;
import com.dershane.taskservice.task.domain.TargetType;
import com.dershane.taskservice.task.domain.Task;
import com.dershane.taskservice.task.domain.TaskDraft;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * KVKK: objects carrying personal data may end up in a log line through string concatenation or an
 * exception message. Their {@code toString} must not reveal names, student numbers or free text
 * written by or about a child (submission texts, feedback) or the teacher's task texts.
 */
class PersonalDataMaskingTest {

  private static final String NAME = "Zeynep Öztürk";
  private static final String NUMBER = "998877";
  private static final String TEXT = "Solved the integral problems on page forty";
  private static final List<String> SECRETS = List.of(NAME, NUMBER, TEXT);

  private final UUID id = UUID.randomUUID();
  private final LocalDate day = LocalDate.of(2026, 11, 9);
  private final Instant now = Instant.parse("2026-11-06T07:00:00Z");

  private void assertMasked(Object value) {
    assertThat(value.toString()).doesNotContain(SECRETS);
  }

  @Test
  void rosterObjectsAreMasked() {
    assertMasked(new RosterEntryView(id, NUMBER, NAME));
    assertMasked(MembershipReplica.open(id, id, id, id, day, NUMBER, NAME));
    assertMasked(new RecordMembershipAddedCommand(id, id, id, id, NUMBER, NAME, day));
    assertMasked(new MembershipReplicaRequest(id, id, id, NUMBER, NAME, day));
  }

  @Test
  void taskObjectsAreMasked() {
    TaskDraft draft =
        new TaskDraft(
            id,
            id,
            TEXT,
            TEXT,
            TargetType.CLASSROOM,
            id,
            Recurrence.WEEKLY,
            Set.of(DayOfWeek.MONDAY),
            day,
            null,
            LocalTime.NOON,
            List.of());
    assertMasked(draft);
    assertMasked(Task.create(draft, day, now, java.time.ZoneOffset.UTC));
    assertMasked(
        new CreateTaskCommand(
            TEXT,
            TEXT,
            TargetType.CLASSROOM,
            id,
            Recurrence.DAILY,
            Set.of(),
            day,
            null,
            LocalTime.NOON,
            List.of()));
    assertMasked(
        new CreateTaskRequest(
            TEXT,
            TEXT,
            TargetType.CLASSROOM,
            id,
            Recurrence.DAILY,
            Set.of(),
            day,
            null,
            LocalTime.NOON,
            List.of()));
    assertMasked(new UpdateTaskCommand(TEXT, TEXT, List.of()));
    assertMasked(new UpdateTaskRequest(TEXT, TEXT, List.of()));
    assertMasked(
        new TaskView(
            id,
            id,
            id,
            TEXT,
            TEXT,
            TargetType.CLASSROOM,
            id,
            Recurrence.DAILY,
            Set.of(),
            day,
            null,
            LocalTime.NOON,
            List.of(),
            true,
            now));
    assertMasked(
        new TaskResponse(
            id,
            id,
            id,
            TEXT,
            TEXT,
            TargetType.CLASSROOM,
            id,
            Recurrence.DAILY,
            Set.of(),
            day,
            null,
            LocalTime.NOON,
            List.of(),
            true,
            now));
  }

  @Test
  void assignmentObjectsAreMasked() {
    Submission submission = new Submission(TEXT, List.of(), now, false);
    Evaluation evaluation = new Evaluation(id, 80, TEXT, now);
    assertMasked(submission);
    assertMasked(evaluation);
    assertMasked(
        new Assignment(
            id, 0L, id, id, day, now, AssignmentStatus.GRADED, false, now, submission, evaluation));
    assertMasked(new SubmitAssignmentCommand(TEXT, List.of()));
    assertMasked(new SubmitAssignmentRequest(TEXT, List.of()));
    assertMasked(new EvaluateAssignmentCommand(80, TEXT));
    assertMasked(new EvaluateAssignmentRequest(80, TEXT));
    SubmissionView submissionView = new SubmissionView(TEXT, List.of(), now, false);
    EvaluationView evaluationView = new EvaluationView(id, 80, TEXT, now);
    assertMasked(submissionView);
    assertMasked(evaluationView);
    assertMasked(
        new AssignmentDetailView(
            id,
            id,
            TEXT,
            TEXT,
            List.of(),
            id,
            id,
            id,
            day,
            now,
            AssignmentStatus.GRADED,
            now,
            submissionView,
            evaluationView));
    assertMasked(new SubmissionResponse(TEXT, List.of(), now, false));
    assertMasked(new EvaluationResponse(id, 80, TEXT, now));
    assertMasked(
        new AssignmentDetailResponse(
            id,
            id,
            TEXT,
            TEXT,
            List.of(),
            id,
            id,
            id,
            day,
            now,
            AssignmentStatus.GRADED,
            now,
            new SubmissionResponse(TEXT, List.of(), now, false),
            new EvaluationResponse(id, 80, TEXT, now)));
  }
}
