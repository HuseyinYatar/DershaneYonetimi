package com.dershane.taskservice.assignment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dershane.taskservice.assignment.application.port.out.LoadAssignmentPort;
import com.dershane.taskservice.assignment.application.port.out.SaveAssignmentPort;
import com.dershane.taskservice.assignment.domain.Assignment;
import com.dershane.taskservice.assignment.domain.AssignmentStatus;
import com.dershane.taskservice.shared.application.BusinessCalendar;
import com.dershane.taskservice.shared.application.event.IntegrationEvent;
import com.dershane.taskservice.shared.application.event.IntegrationEvent.TaskDueSoon;
import com.dershane.taskservice.shared.application.event.IntegrationEvent.TaskOverdue;
import com.dershane.taskservice.shared.application.port.out.IntegrationEventPublisher;
import com.dershane.taskservice.support.TestProperties;
import com.dershane.taskservice.task.application.port.in.TaskLoadQuery;
import com.dershane.taskservice.task.domain.Recurrence;
import com.dershane.taskservice.task.domain.TargetType;
import com.dershane.taskservice.task.domain.Task;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
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
class DeadlineBatchTest {

  private static final Instant NOW = Instant.parse("2026-11-09T14:00:00Z");
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 9);
  private static final Duration LEAD = Duration.ofHours(6);

  @Mock private LoadAssignmentPort loadAssignment;
  @Mock private SaveAssignmentPort saveAssignment;
  @Mock private TaskLoadQuery tasks;
  @Mock private IntegrationEventPublisher publisher;

  private DeadlineBatch batch;
  private Task task;

  @BeforeEach
  void setUp() {
    batch =
        new DeadlineBatch(
            loadAssignment,
            saveAssignment,
            tasks,
            publisher,
            new BusinessCalendar(Clock.fixed(NOW, ZoneOffset.UTC), TestProperties.utc()),
            new SimpleMeterRegistry());
    task =
        new Task(
            UUID.randomUUID(),
            0L,
            UUID.randomUUID(),
            UUID.randomUUID(),
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
  }

  private Assignment assignment(Instant dueAt, Instant assignedAt) {
    return new Assignment(
        UUID.randomUUID(),
        0L,
        task.getId(),
        UUID.randomUUID(),
        TODAY,
        dueAt,
        AssignmentStatus.ASSIGNED,
        false,
        assignedAt,
        null,
        null);
  }

  @Test
  void aDueSoonAssignmentIsRemindedOnceWithAnEvent() {
    Assignment due = assignment(NOW.plus(Duration.ofHours(3)), NOW.minus(Duration.ofHours(10)));
    when(loadAssignment.findReminderCandidates(NOW, LEAD, 500)).thenReturn(List.of(due));
    when(tasks.findById(task.getId())).thenReturn(Optional.of(task));

    int reminded = batch.remind(LEAD, 500);

    assertThat(reminded).isEqualTo(1);
    assertThat(due.isReminderSent()).isTrue();
    verify(saveAssignment).save(due);
    ArgumentCaptor<IntegrationEvent> event = ArgumentCaptor.forClass(IntegrationEvent.class);
    verify(publisher).publish(event.capture());
    assertThat(event.getValue()).isInstanceOf(TaskDueSoon.class);
  }

  @Test
  void aCandidateThatIsNoLongerDueGetsNoReminder() {
    Assignment fresh = assignment(NOW.plus(Duration.ofHours(3)), NOW.minus(Duration.ofMinutes(5)));
    when(loadAssignment.findReminderCandidates(NOW, LEAD, 500)).thenReturn(List.of(fresh));

    assertThat(batch.remind(LEAD, 500)).isZero();

    verify(publisher, never()).publish(any());
    verify(saveAssignment, never()).save(any());
  }

  @Test
  void aPastDueAssignmentBecomesOverdueWithAnEvent() {
    Assignment late = assignment(NOW.minusSeconds(30), NOW.minus(Duration.ofDays(1)));
    when(loadAssignment.findOverdueCandidates(NOW, 500)).thenReturn(List.of(late));
    when(tasks.findById(task.getId())).thenReturn(Optional.of(task));

    int overdue = batch.markOverdue(500);

    assertThat(overdue).isEqualTo(1);
    assertThat(late.getStatus()).isEqualTo(AssignmentStatus.OVERDUE);
    ArgumentCaptor<IntegrationEvent> event = ArgumentCaptor.forClass(IntegrationEvent.class);
    verify(publisher).publish(event.capture());
    assertThat(event.getValue()).isInstanceOf(TaskOverdue.class);
  }

  @Test
  void nothingDueMeansNoWork() {
    when(loadAssignment.findOverdueCandidates(NOW, 500)).thenReturn(List.of());

    assertThat(batch.markOverdue(500)).isZero();

    verify(publisher, never()).publish(any());
  }
}
