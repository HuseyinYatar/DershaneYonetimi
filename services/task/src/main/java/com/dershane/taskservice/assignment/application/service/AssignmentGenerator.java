package com.dershane.taskservice.assignment.application.service;

import com.dershane.taskservice.assignment.application.port.out.LoadAssignmentPort;
import com.dershane.taskservice.assignment.application.port.out.SaveAssignmentPort;
import com.dershane.taskservice.assignment.domain.Assignment;
import com.dershane.taskservice.roster.application.port.in.ClassroomRosterQuery;
import com.dershane.taskservice.roster.application.port.in.MembershipQuery;
import com.dershane.taskservice.roster.application.port.in.RosterEntryView;
import com.dershane.taskservice.shared.application.BusinessCalendar;
import com.dershane.taskservice.shared.application.MetricNames;
import com.dershane.taskservice.shared.application.TaskProperties;
import com.dershane.taskservice.shared.application.event.IntegrationEvent.TaskAssigned;
import com.dershane.taskservice.shared.application.port.out.IntegrationEventPublisher;
import com.dershane.taskservice.task.domain.Task;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hands out the assignments of one task. Every public method is one transaction, so a caller that
 * loops over tasks (the daily job) gets one transaction per task. Existing assignments are looked
 * up first instead of relying on the unique constraint, which would poison the transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class AssignmentGenerator {

  private final LoadAssignmentPort loadAssignment;
  private final SaveAssignmentPort saveAssignment;
  private final ClassroomRosterQuery roster;
  private final MembershipQuery memberships;
  private final IntegrationEventPublisher publisher;
  private final BusinessCalendar calendar;
  private final MeterRegistry meters;
  private final TaskProperties properties;

  /** Hands the task's occurrence of {@code day} to every student it targets that day. */
  @Transactional
  int generateForTask(Task task, LocalDate day) {
    return create(task, day, targetedStudents(task, day));
  }

  /** Hands the task's occurrence of {@code day} to one student only. */
  @Transactional
  int generateForStudent(Task task, LocalDate day, UUID studentId) {
    return create(task, day, List.of(studentId));
  }

  private List<UUID> targetedStudents(Task task, LocalDate day) {
    return switch (task.getTargetType()) {
      case CLASSROOM ->
          roster.rosterOn(task.getTargetId(), day).stream()
              .map(RosterEntryView::studentId)
              .toList();
      case STUDENT -> {
        if (memberships.activeMembershipOf(task.getTargetId(), day).isPresent()) {
          yield List.of(task.getTargetId());
        }
        log.atWarn()
            .addKeyValue("taskId", task.getId())
            .addKeyValue("studentId", task.getTargetId())
            .addKeyValue("day", day)
            .log("Student has no active membership; the individual task is skipped");
        yield List.of();
      }
      default -> List.of();
    };
  }

  private int create(Task task, LocalDate day, Collection<UUID> studentIds) {
    LocalDate occurrence = task.occurrenceFor(day);
    Instant dueAt = task.dueAt(occurrence, properties.timeZone());
    Instant now = calendar.now();
    if (!dueAt.isAfter(now)) {
      log.atDebug()
          .addKeyValue("taskId", task.getId())
          .addKeyValue("occurrenceDate", occurrence)
          .log("Due time already passed; no assignments handed out");
      return 0;
    }
    Set<UUID> existing = loadAssignment.findStudentIdsWithAssignment(task.getId(), occurrence);
    int created = 0;
    for (UUID studentId : studentIds) {
      if (existing.contains(studentId)) {
        log.atDebug()
            .addKeyValue("taskId", task.getId())
            .addKeyValue("studentId", studentId)
            .log("Assignment already exists; skipped");
        continue;
      }
      Assignment saved =
          saveAssignment.save(Assignment.assign(task.getId(), studentId, occurrence, dueAt, now));
      publisher.publish(
          new TaskAssigned(
              saved.getId(),
              task.getId(),
              studentId,
              task.getBranchId(),
              task.getTitle(),
              dueAt,
              task.getRecurrence().name()));
      meters.counter(MetricNames.ASSIGNMENTS_GENERATED).increment();
      created++;
    }
    log.atInfo()
        .addKeyValue("taskId", task.getId())
        .addKeyValue("occurrenceDate", occurrence)
        .addKeyValue("created", created)
        .log("Assignments handed out");
    return created;
  }
}
