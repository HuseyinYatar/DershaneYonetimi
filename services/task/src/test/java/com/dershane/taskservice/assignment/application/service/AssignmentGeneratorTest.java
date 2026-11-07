package com.dershane.taskservice.assignment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dershane.taskservice.assignment.application.port.out.LoadAssignmentPort;
import com.dershane.taskservice.assignment.application.port.out.SaveAssignmentPort;
import com.dershane.taskservice.assignment.domain.Assignment;
import com.dershane.taskservice.roster.application.port.in.ActiveMembershipView;
import com.dershane.taskservice.roster.application.port.in.ClassroomRosterQuery;
import com.dershane.taskservice.roster.application.port.in.MembershipQuery;
import com.dershane.taskservice.roster.application.port.in.RosterEntryView;
import com.dershane.taskservice.shared.application.BusinessCalendar;
import com.dershane.taskservice.shared.application.event.IntegrationEvent;
import com.dershane.taskservice.shared.application.event.IntegrationEvent.TaskAssigned;
import com.dershane.taskservice.shared.application.port.out.IntegrationEventPublisher;
import com.dershane.taskservice.support.TestProperties;
import com.dershane.taskservice.task.domain.Recurrence;
import com.dershane.taskservice.task.domain.TargetType;
import com.dershane.taskservice.task.domain.Task;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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

@ExtendWith(MockitoExtension.class)
class AssignmentGeneratorTest {

  private static final Instant NOW = Instant.parse("2026-11-09T08:00:00Z");
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 9);

  @Mock private LoadAssignmentPort loadAssignment;
  @Mock private SaveAssignmentPort saveAssignment;
  @Mock private ClassroomRosterQuery roster;
  @Mock private MembershipQuery memberships;
  @Mock private IntegrationEventPublisher publisher;

  private final UUID classroomId = UUID.randomUUID();
  private final UUID ali = UUID.randomUUID();
  private final UUID ayse = UUID.randomUUID();

  private AssignmentGenerator generator;

  @BeforeEach
  void setUp() {
    generator =
        new AssignmentGenerator(
            loadAssignment,
            saveAssignment,
            roster,
            memberships,
            publisher,
            new BusinessCalendar(Clock.fixed(NOW, ZoneOffset.UTC), TestProperties.utc()),
            new SimpleMeterRegistry(),
            TestProperties.utc());
    org.mockito.Mockito.lenient()
        .when(saveAssignment.save(any()))
        .thenAnswer(
            invocation -> {
              Assignment a = invocation.getArgument(0);
              return new Assignment(
                  UUID.randomUUID(),
                  0L,
                  a.getTaskId(),
                  a.getStudentId(),
                  a.getOccurrenceDate(),
                  a.getDueAt(),
                  a.getStatus(),
                  false,
                  a.getAssignedAt(),
                  null,
                  null);
            });
  }

  private Task task(TargetType type, UUID targetId, Recurrence recurrence, LocalDate end) {
    return new Task(
        UUID.randomUUID(),
        0L,
        UUID.randomUUID(),
        UUID.randomUUID(),
        "Chapter 4",
        "",
        type,
        targetId,
        recurrence,
        Set.of(),
        TODAY.minusDays(1),
        end,
        LocalTime.of(20, 0),
        List.of(),
        true);
  }

  @Test
  void everyClassroomMemberGetsAnAssignmentAndATaskAssignedEvent() {
    Task task = task(TargetType.CLASSROOM, classroomId, Recurrence.DAILY, null);
    when(roster.rosterOn(classroomId, TODAY))
        .thenReturn(
            List.of(new RosterEntryView(ali, "1", "Ali"), new RosterEntryView(ayse, "2", "Ayse")));
    when(loadAssignment.findStudentIdsWithAssignment(task.getId(), TODAY)).thenReturn(Set.of());

    int created = generator.generateForTask(task, TODAY);

    assertThat(created).isEqualTo(2);
    ArgumentCaptor<IntegrationEvent> events = ArgumentCaptor.forClass(IntegrationEvent.class);
    verify(publisher, org.mockito.Mockito.times(2)).publish(events.capture());
    TaskAssigned first = (TaskAssigned) events.getAllValues().getFirst();
    assertThat(first.title()).isEqualTo("Chapter 4");
    assertThat(first.recurrence()).isEqualTo("DAILY");
    assertThat(first.dueAt()).isEqualTo(Instant.parse("2026-11-09T20:00:00Z"));
  }

  @Test
  void studentsWhoAlreadyHaveTheOccurrenceAreSkipped() {
    Task task = task(TargetType.CLASSROOM, classroomId, Recurrence.DAILY, null);
    when(roster.rosterOn(classroomId, TODAY))
        .thenReturn(
            List.of(new RosterEntryView(ali, "1", "Ali"), new RosterEntryView(ayse, "2", "Ayse")));
    when(loadAssignment.findStudentIdsWithAssignment(task.getId(), TODAY)).thenReturn(Set.of(ali));

    assertThat(generator.generateForTask(task, TODAY)).isEqualTo(1);
  }

  @Test
  void nothingIsHandedOutWhenTheDueTimeAlreadyPassed() {
    Task task = task(TargetType.CLASSROOM, classroomId, Recurrence.ONCE, TODAY);
    Task lateTask =
        new Task(
            task.getId(),
            0L,
            task.getBranchId(),
            task.getTeacherId(),
            "T",
            "",
            TargetType.CLASSROOM,
            classroomId,
            Recurrence.DAILY,
            Set.of(),
            TODAY,
            null,
            LocalTime.of(7, 0),
            List.of(),
            true);

    assertThat(generator.generateForTask(lateTask, TODAY)).isZero();

    verify(saveAssignment, never()).save(any());
    verify(publisher, never()).publish(any());
  }

  @Test
  void anIndividualTaskGoesToItsStudentWhileTheyAreInAClassroom() {
    Task task = task(TargetType.STUDENT, ali, Recurrence.DAILY, null);
    when(memberships.activeMembershipOf(ali, TODAY))
        .thenReturn(Optional.of(new ActiveMembershipView(classroomId, task.getBranchId())));
    when(loadAssignment.findStudentIdsWithAssignment(task.getId(), TODAY)).thenReturn(Set.of());

    assertThat(generator.generateForTask(task, TODAY)).isEqualTo(1);
  }

  @Test
  void anIndividualTaskIsSkippedWhenTheStudentHasNoMembership() {
    Task task = task(TargetType.STUDENT, ali, Recurrence.DAILY, null);
    when(memberships.activeMembershipOf(ali, TODAY)).thenReturn(Optional.empty());
    when(loadAssignment.findStudentIdsWithAssignment(task.getId(), TODAY)).thenReturn(Set.of());

    assertThat(generator.generateForTask(task, TODAY)).isZero();
  }

  @Test
  void aOneOffTaskIsFiledUnderItsStartDateEvenWhenHandedOutLater() {
    Task task = task(TargetType.CLASSROOM, classroomId, Recurrence.ONCE, TODAY.plusDays(2));
    when(loadAssignment.findStudentIdsWithAssignment(task.getId(), TODAY.minusDays(1)))
        .thenReturn(Set.of());

    assertThat(generator.generateForStudent(task, TODAY, ali)).isEqualTo(1);

    ArgumentCaptor<Assignment> saved = ArgumentCaptor.forClass(Assignment.class);
    verify(saveAssignment).save(saved.capture());
    assertThat(saved.getValue().getOccurrenceDate()).isEqualTo(TODAY.minusDays(1));
    assertThat(saved.getValue().getDueAt()).isEqualTo(Instant.parse("2026-11-11T20:00:00Z"));
  }
}
