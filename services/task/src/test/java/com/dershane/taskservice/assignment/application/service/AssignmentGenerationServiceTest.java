package com.dershane.taskservice.assignment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dershane.taskservice.assignment.application.port.in.GenerateAssignmentsResult;
import com.dershane.taskservice.assignment.application.port.out.LoadAssignmentPort;
import com.dershane.taskservice.assignment.application.port.out.SaveAssignmentPort;
import com.dershane.taskservice.assignment.domain.Assignment;
import com.dershane.taskservice.assignment.domain.AssignmentStatus;
import com.dershane.taskservice.shared.application.BusinessCalendar;
import com.dershane.taskservice.support.TestProperties;
import com.dershane.taskservice.task.application.port.in.TaskLoadQuery;
import com.dershane.taskservice.task.domain.Recurrence;
import com.dershane.taskservice.task.domain.TargetType;
import com.dershane.taskservice.task.domain.Task;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.DayOfWeek;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssignmentGenerationServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-09T08:00:00Z");
  // A Monday.
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 9);

  @Mock private TaskLoadQuery tasks;
  @Mock private AssignmentGenerator generator;
  @Mock private LoadAssignmentPort loadAssignment;
  @Mock private SaveAssignmentPort saveAssignment;

  private final UUID studentId = UUID.randomUUID();
  private final UUID classroomId = UUID.randomUUID();

  private AssignmentGenerationService service;

  @BeforeEach
  void setUp() {
    service =
        new AssignmentGenerationService(
            tasks,
            generator,
            loadAssignment,
            saveAssignment,
            new BusinessCalendar(Clock.fixed(NOW, ZoneOffset.UTC), TestProperties.utc()),
            new SimpleMeterRegistry());
  }

  private Task task(
      Recurrence recurrence, Set<DayOfWeek> days, LocalDate start, LocalDate end, TargetType type) {
    return new Task(
        UUID.randomUUID(),
        0L,
        UUID.randomUUID(),
        UUID.randomUUID(),
        "Problems",
        "",
        type,
        classroomId,
        recurrence,
        days,
        start,
        end,
        LocalTime.of(20, 0),
        List.of(),
        true);
  }

  @Test
  void theDailyJobHandsOutOnlyTasksThatOccurToday() {
    Task daily = task(Recurrence.DAILY, Set.of(), TODAY.minusDays(3), null, TargetType.CLASSROOM);
    Task tuesdays =
        task(Recurrence.WEEKLY, Set.of(DayOfWeek.TUESDAY), TODAY, null, TargetType.CLASSROOM);
    when(tasks.findActiveOn(TODAY)).thenReturn(List.of(daily, tuesdays));
    when(generator.generateForTask(daily, TODAY)).thenReturn(4);

    GenerateAssignmentsResult result = service.generateToday();

    assertThat(result).isEqualTo(new GenerateAssignmentsResult(1, 4, 0));
    verify(generator, never()).generateForTask(tuesdays, TODAY);
  }

  @Test
  void aFailingTaskIsCountedAndTheOthersStillRun() {
    Task broken = task(Recurrence.DAILY, Set.of(), TODAY, null, TargetType.CLASSROOM);
    Task fine = task(Recurrence.DAILY, Set.of(), TODAY, null, TargetType.CLASSROOM);
    when(tasks.findActiveOn(TODAY)).thenReturn(List.of(broken, fine));
    doThrow(new IllegalStateException("boom")).when(generator).generateForTask(broken, TODAY);
    when(generator.generateForTask(fine, TODAY)).thenReturn(2);

    GenerateAssignmentsResult result = service.generateToday();

    assertThat(result).isEqualTo(new GenerateAssignmentsResult(2, 2, 1));
  }

  @Test
  void nothingOccurringTodayIsNotAnError() {
    when(tasks.findActiveOn(TODAY)).thenReturn(List.of());

    assertThat(service.generateToday()).isEqualTo(new GenerateAssignmentsResult(0, 0, 0));
  }

  @Test
  void aNewTaskThatOccursTodayGetsItsAssignmentsAtOnce() {
    Task created = task(Recurrence.DAILY, Set.of(), TODAY, null, TargetType.CLASSROOM);
    when(tasks.findById(created.getId())).thenReturn(Optional.of(created));

    service.generateFor(created.getId());

    verify(generator).generateForTask(created, TODAY);
  }

  @Test
  void aNewTaskThatStartsLaterWaitsForTheDailyJob() {
    Task later = task(Recurrence.DAILY, Set.of(), TODAY.plusDays(2), null, TargetType.CLASSROOM);
    when(tasks.findById(later.getId())).thenReturn(Optional.of(later));

    service.generateFor(later.getId());

    verify(generator, never()).generateForTask(any(), any());
  }

  @Test
  void aJoiningStudentGetsTodaysRecurringAndStartedOneOffTasks() {
    Task daily = task(Recurrence.DAILY, Set.of(), TODAY.minusDays(1), null, TargetType.CLASSROOM);
    Task tuesdays =
        task(
            Recurrence.WEEKLY,
            Set.of(DayOfWeek.TUESDAY),
            TODAY.minusDays(7),
            null,
            TargetType.CLASSROOM);
    Task oneOff =
        task(
            Recurrence.ONCE, Set.of(), TODAY.minusDays(2), TODAY.plusDays(3), TargetType.CLASSROOM);
    when(tasks.findActiveTargeting(TargetType.CLASSROOM, classroomId, TODAY))
        .thenReturn(List.of(daily, tuesdays, oneOff));

    service.studentJoined(studentId, classroomId, TODAY);

    verify(generator).generateForStudent(daily, TODAY, studentId);
    verify(generator).generateForStudent(oneOff, TODAY, studentId);
    verify(generator, never()).generateForStudent(tuesdays, TODAY, studentId);
  }

  @Test
  void aMembershipThatStartsLaterIsLeftToTheDailyJob() {
    service.studentJoined(studentId, classroomId, TODAY.plusDays(1));

    verify(tasks, never()).findActiveTargeting(any(), any(), any());
  }

  @Test
  void aLeavingStudentLosesOnlyTheirOpenAssignments() {
    Assignment open =
        Assignment.assign(
            UUID.randomUUID(), studentId, TODAY, NOW.plusSeconds(3600), NOW.minusSeconds(60));
    Assignment alreadyDue =
        Assignment.assign(
            UUID.randomUUID(), studentId, TODAY, NOW.minusSeconds(1), NOW.minusSeconds(60));
    when(loadAssignment.findOpenForClassroom(studentId, classroomId, NOW))
        .thenReturn(List.of(open, alreadyDue));

    service.studentLeft(studentId, classroomId);

    assertThat(open.getStatus()).isEqualTo(AssignmentStatus.CANCELLED);
    assertThat(alreadyDue.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
    verify(saveAssignment).save(open);
    verify(saveAssignment, never()).save(alreadyDue);
  }

  @Test
  void leavingWithNothingOpenSavesNothing() {
    when(loadAssignment.findOpenForClassroom(studentId, classroomId, NOW)).thenReturn(List.of());

    service.studentLeft(studentId, classroomId);

    verify(saveAssignment, never()).save(any());
    verify(generator, never()).generateForTask(any(), any());
    verify(generator, never()).generateForStudent(any(), any(), any());
  }
}
