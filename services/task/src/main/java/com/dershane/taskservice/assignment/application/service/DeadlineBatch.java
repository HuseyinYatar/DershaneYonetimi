package com.dershane.taskservice.assignment.application.service;

import com.dershane.taskservice.assignment.application.port.out.LoadAssignmentPort;
import com.dershane.taskservice.assignment.application.port.out.SaveAssignmentPort;
import com.dershane.taskservice.assignment.domain.Assignment;
import com.dershane.taskservice.shared.application.BusinessCalendar;
import com.dershane.taskservice.shared.application.MetricNames;
import com.dershane.taskservice.shared.application.event.IntegrationEvent.TaskDueSoon;
import com.dershane.taskservice.shared.application.event.IntegrationEvent.TaskOverdue;
import com.dershane.taskservice.shared.application.port.out.IntegrationEventPublisher;
import com.dershane.taskservice.task.application.port.in.TaskLoadQuery;
import com.dershane.taskservice.task.domain.Task;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** One batch of reminders or overdue marking per transaction. */
@Slf4j
@Component
@RequiredArgsConstructor
class DeadlineBatch {

  private final LoadAssignmentPort loadAssignment;
  private final SaveAssignmentPort saveAssignment;
  private final TaskLoadQuery tasks;
  private final IntegrationEventPublisher publisher;
  private final BusinessCalendar calendar;
  private final MeterRegistry meters;

  /** Sends {@code TaskDueSoon} for up to {@code size} assignments; returns how many. */
  @Transactional
  int remind(Duration leadTime, int size) {
    Instant now = calendar.now();
    Map<UUID, Task> cache = new HashMap<>();
    int reminded = 0;
    for (Assignment assignment : loadAssignment.findReminderCandidates(now, leadTime, size)) {
      if (!assignment.reminderDue(now, leadTime)) {
        log.atDebug()
            .addKeyValue("assignmentId", assignment.getId())
            .log("Reminder candidate is no longer due; skipped");
        continue;
      }
      Task task = taskOf(assignment, cache);
      assignment.markReminderSent();
      saveAssignment.save(assignment);
      publisher.publish(
          new TaskDueSoon(
              assignment.getId(),
              task.getId(),
              assignment.getStudentId(),
              task.getBranchId(),
              task.getTitle(),
              assignment.getDueAt()));
      meters.counter(MetricNames.REMINDERS_SENT).increment();
      reminded++;
    }
    return reminded;
  }

  /** Marks up to {@code size} assignments overdue; returns how many. */
  @Transactional
  int markOverdue(int size) {
    Instant now = calendar.now();
    Map<UUID, Task> cache = new HashMap<>();
    int overdue = 0;
    List<Assignment> candidates = loadAssignment.findOverdueCandidates(now, size);
    for (Assignment assignment : candidates) {
      if (!assignment.markOverdue(now)) {
        continue;
      }
      Task task = taskOf(assignment, cache);
      saveAssignment.save(assignment);
      publisher.publish(
          new TaskOverdue(
              assignment.getId(),
              task.getId(),
              assignment.getStudentId(),
              task.getBranchId(),
              task.getTitle(),
              assignment.getDueAt()));
      meters.counter(MetricNames.ASSIGNMENTS_OVERDUE).increment();
      overdue++;
    }
    return overdue;
  }

  private Task taskOf(Assignment assignment, Map<UUID, Task> cache) {
    return cache.computeIfAbsent(
        assignment.getTaskId(),
        id ->
            tasks
                .findById(id)
                .orElseThrow(() -> new IllegalStateException("Task of an assignment is missing")));
  }
}
