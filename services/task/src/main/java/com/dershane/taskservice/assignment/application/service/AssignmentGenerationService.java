package com.dershane.taskservice.assignment.application.service;

import com.dershane.taskservice.assignment.application.port.in.GenerateAssignmentsResult;
import com.dershane.taskservice.assignment.application.port.in.GenerateForNewTaskUseCase;
import com.dershane.taskservice.assignment.application.port.in.GenerateTodayAssignmentsUseCase;
import com.dershane.taskservice.assignment.application.port.in.StudentJoinedClassroomUseCase;
import com.dershane.taskservice.assignment.application.port.in.StudentLeftClassroomUseCase;
import com.dershane.taskservice.assignment.application.port.out.LoadAssignmentPort;
import com.dershane.taskservice.assignment.application.port.out.SaveAssignmentPort;
import com.dershane.taskservice.assignment.domain.Assignment;
import com.dershane.taskservice.shared.application.BusinessCalendar;
import com.dershane.taskservice.shared.application.MetricNames;
import com.dershane.taskservice.task.application.port.in.TaskLoadQuery;
import com.dershane.taskservice.task.domain.Recurrence;
import com.dershane.taskservice.task.domain.TargetType;
import com.dershane.taskservice.task.domain.Task;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.annotation.Observed;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The triggers that hand out and cancel assignments; the work itself is in the generator. */
@Slf4j
@Service
@RequiredArgsConstructor
class AssignmentGenerationService
    implements GenerateTodayAssignmentsUseCase,
        GenerateForNewTaskUseCase,
        StudentJoinedClassroomUseCase,
        StudentLeftClassroomUseCase {

  private final TaskLoadQuery tasks;
  private final AssignmentGenerator generator;
  private final LoadAssignmentPort loadAssignment;
  private final SaveAssignmentPort saveAssignment;
  private final BusinessCalendar calendar;
  private final MeterRegistry meters;

  /**
   * Not transactional on purpose: each task is handed out in its own transaction, so one failing
   * task does not roll back the others.
   */
  @Override
  @Observed(name = "assignment.generate-today")
  public GenerateAssignmentsResult generateToday() {
    LocalDate today = calendar.today();
    List<Task> occurring =
        tasks.findActiveOn(today).stream().filter(task -> task.occursOn(today)).toList();
    if (occurring.isEmpty()) {
      log.atDebug().addKeyValue("today", today).log("No task occurs today; nothing to generate");
      return new GenerateAssignmentsResult(0, 0, 0);
    }
    int created = 0;
    int failed = 0;
    for (Task task : occurring) {
      try {
        created += generator.generateForTask(task, today);
      } catch (RuntimeException ex) {
        failed++;
        meters.counter(MetricNames.GENERATION_FAILURES).increment();
        log.atError()
            .addKeyValue("taskId", task.getId())
            .setCause(ex)
            .log("Generating assignments failed; continuing with the next task");
      }
    }
    log.atInfo()
        .addKeyValue("tasks", occurring.size())
        .addKeyValue("created", created)
        .addKeyValue("failed", failed)
        .log("Assignment generation finished");
    return new GenerateAssignmentsResult(occurring.size(), created, failed);
  }

  /** Runs inside the transaction that created the task (it is called from its event). */
  @Override
  @Transactional
  public void generateFor(UUID taskId) {
    LocalDate today = calendar.today();
    Task task =
        tasks
            .findById(taskId)
            .orElseThrow(() -> new IllegalStateException("Created task not found: " + taskId));
    if (!task.occursOn(today)) {
      log.atDebug()
          .addKeyValue("taskId", taskId)
          .log("New task does not occur today; the daily job hands it out later");
      return;
    }
    generator.generateForTask(task, today);
  }

  @Override
  @Transactional
  public void studentJoined(UUID studentId, UUID classroomId, LocalDate startDate) {
    LocalDate today = calendar.today();
    if (startDate.isAfter(today)) {
      log.atDebug()
          .addKeyValue("studentId", studentId)
          .addKeyValue("classroomId", classroomId)
          .addKeyValue("startDate", startDate)
          .log("Membership starts later; the daily job hands out the tasks then");
      return;
    }
    for (Task task : tasks.findActiveTargeting(TargetType.CLASSROOM, classroomId, today)) {
      // A one-off task already started; a recurring one only counts on the days it occurs.
      if (task.getRecurrence() == Recurrence.ONCE || task.occursOn(today)) {
        generator.generateForStudent(task, today, studentId);
      }
    }
  }

  @Override
  @Transactional
  @Observed(name = "assignment.cancel-for-student")
  public void studentLeft(UUID studentId, UUID classroomId) {
    Instant now = calendar.now();
    int cancelled = 0;
    for (Assignment assignment : loadAssignment.findOpenForClassroom(studentId, classroomId, now)) {
      if (assignment.cancelIfOpen(now)) {
        saveAssignment.save(assignment);
        cancelled++;
      }
    }
    if (cancelled == 0) {
      log.atDebug()
          .addKeyValue("studentId", studentId)
          .addKeyValue("classroomId", classroomId)
          .log("Student left the classroom with no open assignments to cancel");
      return;
    }
    log.atInfo()
        .addKeyValue("studentId", studentId)
        .addKeyValue("classroomId", classroomId)
        .addKeyValue("cancelled", cancelled)
        .log("Open assignments cancelled");
  }
}
