package com.dershane.taskservice.task.application.service;

import static com.dershane.taskservice.support.TestJwts.actor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dershane.taskservice.roster.application.port.in.ActiveMembershipView;
import com.dershane.taskservice.roster.application.port.in.MembershipQuery;
import com.dershane.taskservice.shared.application.Actor;
import com.dershane.taskservice.shared.application.BranchAccessPolicy;
import com.dershane.taskservice.shared.application.BusinessCalendar;
import com.dershane.taskservice.shared.domain.DomainException;
import com.dershane.taskservice.shared.domain.ErrorCode;
import com.dershane.taskservice.support.TestProperties;
import com.dershane.taskservice.task.application.event.TaskCreated;
import com.dershane.taskservice.task.application.port.in.CreateTaskCommand;
import com.dershane.taskservice.task.application.port.in.TaskView;
import com.dershane.taskservice.task.application.port.in.UpdateTaskCommand;
import com.dershane.taskservice.task.application.port.out.LoadTaskPort;
import com.dershane.taskservice.task.application.port.out.SaveTaskPort;
import com.dershane.taskservice.task.application.port.out.TaskQueryPort;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-09T08:00:00Z");
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 9);

  @Mock private LoadTaskPort loadTask;
  @Mock private SaveTaskPort saveTask;
  @Mock private TaskQueryPort taskQuery;
  @Mock private MembershipQuery memberships;
  @Mock private ApplicationEventPublisher applicationEvents;

  private final UUID branchId = UUID.randomUUID();
  private final UUID classroomId = UUID.randomUUID();
  private final UUID studentId = UUID.randomUUID();
  private final Actor teacher = actor(UUID.randomUUID(), branchId, "TEACHER");
  private final Actor otherTeacher = actor(UUID.randomUUID(), branchId, "TEACHER");
  private final Actor admin = actor(UUID.randomUUID(), branchId, "ADMIN");
  private final Actor otherBranchAdmin = actor(UUID.randomUUID(), UUID.randomUUID(), "ADMIN");

  private TaskService service;

  @BeforeEach
  void setUp() {
    BusinessCalendar calendar =
        new BusinessCalendar(Clock.fixed(NOW, ZoneOffset.UTC), TestProperties.utc());
    service =
        new TaskService(
            loadTask,
            saveTask,
            taskQuery,
            memberships,
            new BranchAccessPolicy(),
            calendar,
            applicationEvents,
            TestProperties.inZone(ZoneOffset.UTC, 2));
  }

  private static void assertRejected(Runnable action, ErrorCode expected) {
    assertThatExceptionOfType(DomainException.class)
        .isThrownBy(action::run)
        .satisfies(ex -> assertThat(ex.getErrorCode()).isEqualTo(expected));
  }

  private CreateTaskCommand classroomTask(List<UUID> files) {
    return new CreateTaskCommand(
        "Problems",
        "Solve",
        TargetType.CLASSROOM,
        classroomId,
        Recurrence.DAILY,
        Set.of(),
        TODAY,
        null,
        LocalTime.of(20, 0),
        files);
  }

  private Task stored(Actor owner) {
    return new Task(
        UUID.randomUUID(),
        3L,
        branchId,
        owner.id(),
        "Problems",
        "",
        TargetType.CLASSROOM,
        classroomId,
        Recurrence.DAILY,
        Set.of(),
        TODAY,
        null,
        LocalTime.of(20, 0),
        List.of(),
        true);
  }

  @Test
  void aTeacherCreatesATaskForAClassroomOfTheirBranch() {
    when(memberships.branchOfClassroomOn(classroomId, TODAY)).thenReturn(Optional.of(branchId));
    UUID taskId = UUID.randomUUID();
    when(saveTask.save(any()))
        .thenAnswer(
            invocation -> {
              Task task = invocation.getArgument(0);
              return new Task(
                  taskId,
                  0L,
                  task.getBranchId(),
                  task.getTeacherId(),
                  task.getTitle(),
                  task.getDescription(),
                  task.getTargetType(),
                  task.getTargetId(),
                  task.getRecurrence(),
                  task.getRecurrenceDays(),
                  task.getStartDate(),
                  task.getEndDate(),
                  task.getDueTime(),
                  task.getAttachmentFileIds(),
                  true);
            });
    TaskView view = org.mockito.Mockito.mock(TaskView.class);
    when(taskQuery.findViewById(taskId)).thenReturn(Optional.of(view));

    TaskView created = service.create(teacher, classroomTask(List.of()));

    assertThat(created).isSameAs(view);
    ArgumentCaptor<Task> saved = ArgumentCaptor.forClass(Task.class);
    verify(saveTask).save(saved.capture());
    assertThat(saved.getValue().getBranchId()).isEqualTo(branchId);
    assertThat(saved.getValue().getTeacherId()).isEqualTo(teacher.id());
    verify(applicationEvents).publishEvent(new TaskCreated(taskId));
  }

  @Test
  void aStudentTaskNeedsAnActiveMembershipInTheTeachersBranch() {
    when(memberships.activeMembershipOf(studentId, TODAY))
        .thenReturn(Optional.of(new ActiveMembershipView(classroomId, UUID.randomUUID())));
    CreateTaskCommand command =
        new CreateTaskCommand(
            "Problems",
            "",
            TargetType.STUDENT,
            studentId,
            Recurrence.DAILY,
            Set.of(),
            TODAY,
            null,
            LocalTime.NOON,
            List.of());

    assertRejected(() -> service.create(teacher, command), ErrorCode.TARGET_NOT_IN_BRANCH);
    verify(saveTask, never()).save(any());
  }

  @Test
  void aClassroomWithoutMembersCannotGetATask() {
    when(memberships.branchOfClassroomOn(classroomId, TODAY)).thenReturn(Optional.empty());

    assertRejected(
        () -> service.create(teacher, classroomTask(List.of())), ErrorCode.TARGET_NOT_IN_BRANCH);
  }

  @Test
  void onlyTeachersCreateTasks() {
    assertRejected(() -> service.create(admin, classroomTask(List.of())), ErrorCode.ACCESS_DENIED);
    assertRejected(
        () ->
            service.create(actor(UUID.randomUUID(), branchId, "STUDENT"), classroomTask(List.of())),
        ErrorCode.ACCESS_DENIED);
    verify(saveTask, never()).save(any());
  }

  @Test
  void tooManyAttachmentsAreRejected() {
    List<UUID> files = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    assertRejected(
        () -> service.create(teacher, classroomTask(files)), ErrorCode.TOO_MANY_ATTACHMENTS);
  }

  @Test
  void theTasksTeacherAndABranchAdminMayEdit() {
    Task task = stored(teacher);
    when(loadTask.findById(task.getId())).thenReturn(Optional.of(task));
    when(taskQuery.findViewById(task.getId()))
        .thenReturn(Optional.of(org.mockito.Mockito.mock(TaskView.class)));

    service.update(teacher, task.getId(), new UpdateTaskCommand("New", "Text", List.of()));
    service.update(admin, task.getId(), new UpdateTaskCommand("Newer", "Text", List.of()));

    assertThat(task.getTitle()).isEqualTo("Newer");
    verify(saveTask, org.mockito.Mockito.times(2)).save(task);
  }

  @Test
  void anotherTeacherOrAnotherBranchAdminMayNotEdit() {
    Task task = stored(teacher);
    when(loadTask.findById(task.getId())).thenReturn(Optional.of(task));
    UpdateTaskCommand command = new UpdateTaskCommand("New", "Text", List.of());

    assertRejected(
        () -> service.update(otherTeacher, task.getId(), command), ErrorCode.NOT_TASK_TEACHER);
    assertRejected(
        () -> service.update(otherBranchAdmin, task.getId(), command),
        ErrorCode.BRANCH_SCOPE_VIOLATION);
    verify(saveTask, never()).save(any());
  }

  @Test
  void deactivatingStopsTheTaskAndRepeatingIsRejected() {
    Task task = stored(teacher);
    when(loadTask.findById(task.getId())).thenReturn(Optional.of(task));
    when(taskQuery.findViewById(task.getId()))
        .thenReturn(Optional.of(org.mockito.Mockito.mock(TaskView.class)));

    service.deactivate(teacher, task.getId());

    assertThat(task.isActive()).isFalse();
    assertRejected(() -> service.deactivate(teacher, task.getId()), ErrorCode.TASK_INACTIVE);
  }

  @Test
  void anUnknownTaskIsNotFound() {
    UUID taskId = UUID.randomUUID();
    when(loadTask.findById(taskId)).thenReturn(Optional.empty());

    assertRejected(() -> service.get(teacher, taskId), ErrorCode.TASK_NOT_FOUND);
  }

  @Test
  void teachersListOnlyTheirOwnTasks() {
    service.list(teacher, null, null, true, PageRequest.of(0, 10));

    verify(taskQuery).findViews(null, teacher.id(), true, PageRequest.of(0, 10));
    assertRejected(
        () -> service.list(teacher, otherTeacher.id(), null, null, PageRequest.of(0, 10)),
        ErrorCode.ACCESS_DENIED);
  }

  @Test
  void adminsListTheirBranchAndNotAnother() {
    service.list(admin, null, null, null, PageRequest.of(0, 10));

    verify(taskQuery).findViews(branchId, null, null, PageRequest.of(0, 10));
    assertRejected(
        () -> service.list(admin, null, UUID.randomUUID(), null, PageRequest.of(0, 10)),
        ErrorCode.BRANCH_SCOPE_VIOLATION);
  }

  @Test
  void studentsAndGuardiansListNoTasks() {
    assertRejected(
        () ->
            service.list(
                actor(UUID.randomUUID(), branchId, "STUDENT"),
                null,
                null,
                null,
                PageRequest.of(0, 10)),
        ErrorCode.ACCESS_DENIED);
  }
}
