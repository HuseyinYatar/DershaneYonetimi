package com.dershane.taskservice.assignment.application.service;

import static com.dershane.taskservice.support.TestJwts.actor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dershane.taskservice.assignment.application.port.in.AssignmentDetailView;
import com.dershane.taskservice.assignment.application.port.in.EvaluateAssignmentCommand;
import com.dershane.taskservice.assignment.application.port.in.SubmitAssignmentCommand;
import com.dershane.taskservice.assignment.application.port.out.AssignmentFilter;
import com.dershane.taskservice.assignment.application.port.out.AssignmentQueryPort;
import com.dershane.taskservice.assignment.application.port.out.LoadAssignmentPort;
import com.dershane.taskservice.assignment.application.port.out.SaveAssignmentPort;
import com.dershane.taskservice.assignment.domain.Assignment;
import com.dershane.taskservice.assignment.domain.AssignmentStatus;
import com.dershane.taskservice.roster.application.port.in.GuardianAccessQuery;
import com.dershane.taskservice.shared.application.Actor;
import com.dershane.taskservice.shared.application.BranchAccessPolicy;
import com.dershane.taskservice.shared.application.BusinessCalendar;
import com.dershane.taskservice.shared.application.event.IntegrationEvent;
import com.dershane.taskservice.shared.application.event.IntegrationEvent.SubmissionGraded;
import com.dershane.taskservice.shared.application.port.out.IntegrationEventPublisher;
import com.dershane.taskservice.shared.domain.DomainException;
import com.dershane.taskservice.shared.domain.ErrorCode;
import com.dershane.taskservice.support.TestProperties;
import com.dershane.taskservice.task.application.port.in.GetTaskQuery;
import com.dershane.taskservice.task.application.port.in.TaskLoadQuery;
import com.dershane.taskservice.task.domain.Recurrence;
import com.dershane.taskservice.task.domain.TargetType;
import com.dershane.taskservice.task.domain.Task;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class AssignmentServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-09T08:00:00Z");
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 9);

  @Mock private LoadAssignmentPort loadAssignment;
  @Mock private SaveAssignmentPort saveAssignment;
  @Mock private AssignmentQueryPort assignmentQuery;
  @Mock private TaskLoadQuery tasks;
  @Mock private GetTaskQuery taskAccess;
  @Mock private GuardianAccessQuery guardians;
  @Mock private IntegrationEventPublisher publisher;

  private final UUID branchId = UUID.randomUUID();
  private final Actor student = actor(UUID.randomUUID(), branchId, "STUDENT");
  private final Actor otherStudent = actor(UUID.randomUUID(), branchId, "STUDENT");
  private final Actor teacher = actor(UUID.randomUUID(), branchId, "TEACHER");
  private final Actor otherTeacher = actor(UUID.randomUUID(), branchId, "TEACHER");
  private final Actor guardian = actor(UUID.randomUUID(), branchId, "GUARDIAN");
  private final Actor admin = actor(UUID.randomUUID(), branchId, "ADMIN");
  private final Actor otherBranchAdmin = actor(UUID.randomUUID(), UUID.randomUUID(), "ADMIN");
  private final Actor superAdmin = actor(UUID.randomUUID(), null, "ADMIN", "SUPER_ADMIN");

  private AssignmentService service;
  private Assignment assignment;
  private Task task;

  @BeforeEach
  void setUp() {
    service =
        new AssignmentService(
            loadAssignment,
            saveAssignment,
            assignmentQuery,
            tasks,
            taskAccess,
            guardians,
            new BranchAccessPolicy(),
            new BusinessCalendar(Clock.fixed(NOW, ZoneOffset.UTC), TestProperties.utc()),
            publisher);
    task =
        new Task(
            UUID.randomUUID(),
            0L,
            branchId,
            teacher.id(),
            "Problems",
            "",
            TargetType.CLASSROOM,
            UUID.randomUUID(),
            Recurrence.DAILY,
            Set.of(),
            TODAY,
            null,
            LocalTime.of(20, 0),
            List.of(),
            true);
    assignment =
        new Assignment(
            UUID.randomUUID(),
            1L,
            task.getId(),
            student.id(),
            TODAY,
            NOW.plusSeconds(3600),
            AssignmentStatus.ASSIGNED,
            false,
            NOW.minusSeconds(60),
            null,
            null);
  }

  private static void assertRejected(Runnable action, ErrorCode expected) {
    assertThatExceptionOfType(DomainException.class)
        .isThrownBy(action::run)
        .satisfies(ex -> assertThat(ex.getErrorCode()).isEqualTo(expected));
  }

  private AssignmentDetailView detail() {
    return new AssignmentDetailView(
        assignment.getId(),
        task.getId(),
        "Problems",
        "",
        List.of(),
        teacher.id(),
        branchId,
        student.id(),
        TODAY,
        assignment.getDueAt(),
        AssignmentStatus.ASSIGNED,
        NOW,
        null,
        null);
  }

  private void givenStoredAssignment() {
    when(loadAssignment.findById(assignment.getId())).thenReturn(Optional.of(assignment));
    when(assignmentQuery.findDetailById(assignment.getId())).thenReturn(Optional.of(detail()));
  }

  @Test
  void theStudentSubmitsTheirOwnAssignment() {
    givenStoredAssignment();

    service.submit(student, assignment.getId(), new SubmitAssignmentCommand("Done", List.of()));

    assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.SUBMITTED);
    verify(saveAssignment).save(assignment);
  }

  @Test
  void nobodyElseMaySubmit() {
    when(loadAssignment.findById(assignment.getId())).thenReturn(Optional.of(assignment));
    SubmitAssignmentCommand command = new SubmitAssignmentCommand("Done", List.of());

    assertRejected(
        () -> service.submit(otherStudent, assignment.getId(), command), ErrorCode.ACCESS_DENIED);
    assertRejected(
        () -> service.submit(teacher, assignment.getId(), command), ErrorCode.ACCESS_DENIED);
    verify(saveAssignment, never()).save(any());
  }

  @Test
  void theTasksTeacherEvaluatesAndSubmissionGradedIsPublished() {
    givenStoredAssignment();
    when(tasks.findById(task.getId())).thenReturn(Optional.of(task));
    assignment.submit("answer", List.of(), NOW);

    service.evaluate(teacher, assignment.getId(), new EvaluateAssignmentCommand(85, "Good"));

    assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.GRADED);
    ArgumentCaptor<IntegrationEvent> event = ArgumentCaptor.forClass(IntegrationEvent.class);
    verify(publisher).publish(event.capture());
    SubmissionGraded graded = (SubmissionGraded) event.getValue();
    assertThat(graded.score()).isEqualTo(85);
    assertThat(graded.studentId()).isEqualTo(student.id());
    assertThat(graded.title()).isEqualTo("Problems");
  }

  @Test
  void onlyTheTasksTeacherMayEvaluate() {
    when(loadAssignment.findById(assignment.getId())).thenReturn(Optional.of(assignment));
    when(tasks.findById(task.getId())).thenReturn(Optional.of(task));
    EvaluateAssignmentCommand command = new EvaluateAssignmentCommand(50, null);

    assertRejected(
        () -> service.evaluate(otherTeacher, assignment.getId(), command),
        ErrorCode.NOT_TASK_TEACHER);
    assertRejected(
        () -> service.evaluate(admin, assignment.getId(), command), ErrorCode.NOT_TASK_TEACHER);
    verify(publisher, never()).publish(any());
  }

  @Test
  void aRejectedEvaluationPublishesNothing() {
    when(loadAssignment.findById(assignment.getId())).thenReturn(Optional.of(assignment));
    when(tasks.findById(task.getId())).thenReturn(Optional.of(task));

    assertRejected(
        () ->
            service.evaluate(teacher, assignment.getId(), new EvaluateAssignmentCommand(50, null)),
        ErrorCode.NOT_SUBMITTED);
    verify(publisher, never()).publish(any());
    verify(saveAssignment, never()).save(any());
  }

  @Test
  void anUnknownAssignmentIsNotFound() {
    UUID unknown = UUID.randomUUID();
    when(loadAssignment.findById(unknown)).thenReturn(Optional.empty());

    assertRejected(
        () -> service.submit(student, unknown, new SubmitAssignmentCommand("x", List.of())),
        ErrorCode.ASSIGNMENT_NOT_FOUND);
  }

  @Test
  void studentTeacherBranchAdminAndLinkedGuardianMayViewTheDetail() {
    when(assignmentQuery.findDetailById(assignment.getId())).thenReturn(Optional.of(detail()));
    when(guardians.isGuardianOf(guardian.id(), student.id())).thenReturn(true);

    for (Actor viewer : List.of(student, teacher, admin, superAdmin, guardian)) {
      assertThat(service.get(viewer, assignment.getId()).id()).isEqualTo(assignment.getId());
    }
  }

  @Test
  void strangersMayNotViewTheDetail() {
    when(assignmentQuery.findDetailById(assignment.getId())).thenReturn(Optional.of(detail()));
    when(guardians.isGuardianOf(guardian.id(), student.id())).thenReturn(false);

    for (Actor stranger : List.of(otherStudent, otherTeacher, otherBranchAdmin, guardian)) {
      assertRejected(() -> service.get(stranger, assignment.getId()), ErrorCode.ACCESS_DENIED);
    }
  }

  @Test
  void aStudentListsOnlyTheirOwnAssignments() {
    service.list(student, null, AssignmentStatus.OVERDUE, null, null, PageRequest.of(0, 10));

    verify(assignmentQuery)
        .findSummaries(
            new AssignmentFilter(
                student.id(), null, null, null, AssignmentStatus.OVERDUE, null, null),
            PageRequest.of(0, 10));
    assertRejected(
        () -> service.list(student, otherStudent.id(), null, null, null, PageRequest.of(0, 10)),
        ErrorCode.ACCESS_DENIED);
  }

  @Test
  void aGuardianMustNameALinkedChild() {
    UUID child = UUID.randomUUID();
    when(guardians.isGuardianOf(guardian.id(), child)).thenReturn(true);
    when(guardians.isGuardianOf(guardian.id(), student.id())).thenReturn(false);

    assertRejected(
        () -> service.list(guardian, null, null, null, null, PageRequest.of(0, 10)),
        ErrorCode.STUDENT_FILTER_REQUIRED);
    assertRejected(
        () -> service.list(guardian, student.id(), null, null, null, PageRequest.of(0, 10)),
        ErrorCode.GUARDIAN_NOT_LINKED);
    service.list(guardian, child, null, null, null, PageRequest.of(0, 10));
    verify(assignmentQuery)
        .findSummaries(
            new AssignmentFilter(child, null, null, null, null, null, null), PageRequest.of(0, 10));
  }

  @Test
  void aTeacherListsTheirTasksAndAnAdminTheirBranch() {
    service.list(teacher, null, null, null, null, PageRequest.of(0, 10));
    service.list(admin, null, null, null, null, PageRequest.of(0, 10));

    verify(assignmentQuery)
        .findSummaries(
            new AssignmentFilter(null, teacher.id(), null, null, null, null, null),
            PageRequest.of(0, 10));
    verify(assignmentQuery)
        .findSummaries(
            new AssignmentFilter(null, null, branchId, null, null, null, null),
            PageRequest.of(0, 10));
  }

  @Test
  void theAssignmentsOfATaskNeedTheTaskManager() {
    service.listOfTask(teacher, task.getId(), TODAY, null, PageRequest.of(0, 10));

    verify(taskAccess).requireManager(teacher, task.getId());
    verify(assignmentQuery)
        .findSummaries(
            new AssignmentFilter(null, null, null, task.getId(), null, TODAY, TODAY),
            PageRequest.of(0, 10));
  }
}
