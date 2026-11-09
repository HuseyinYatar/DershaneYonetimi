package com.dershane.taskservice.assignment.application.service;

import com.dershane.taskservice.assignment.application.port.in.AssignmentDetailView;
import com.dershane.taskservice.assignment.application.port.in.AssignmentSummaryView;
import com.dershane.taskservice.assignment.application.port.in.EvaluateAssignmentCommand;
import com.dershane.taskservice.assignment.application.port.in.EvaluateAssignmentUseCase;
import com.dershane.taskservice.assignment.application.port.in.GetAssignmentQuery;
import com.dershane.taskservice.assignment.application.port.in.ListAssignmentsQuery;
import com.dershane.taskservice.assignment.application.port.in.SubmitAssignmentCommand;
import com.dershane.taskservice.assignment.application.port.in.SubmitAssignmentUseCase;
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
import com.dershane.taskservice.shared.application.event.IntegrationEvent.SubmissionGraded;
import com.dershane.taskservice.shared.application.port.out.IntegrationEventPublisher;
import com.dershane.taskservice.shared.domain.ErrorCode;
import com.dershane.taskservice.shared.domain.Rejections;
import com.dershane.taskservice.task.application.port.in.GetTaskQuery;
import com.dershane.taskservice.task.application.port.in.TaskLoadQuery;
import com.dershane.taskservice.task.domain.Task;
import io.micrometer.observation.annotation.Observed;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
class AssignmentService
    implements SubmitAssignmentUseCase,
        EvaluateAssignmentUseCase,
        GetAssignmentQuery,
        ListAssignmentsQuery {

  private final LoadAssignmentPort loadAssignment;
  private final SaveAssignmentPort saveAssignment;
  private final AssignmentQueryPort assignmentQuery;
  private final TaskLoadQuery tasks;
  private final GetTaskQuery taskAccess;
  private final GuardianAccessQuery guardians;
  private final BranchAccessPolicy branchAccess;
  private final BusinessCalendar calendar;
  private final IntegrationEventPublisher publisher;

  @Override
  @Transactional
  @Observed(name = "assignment.submit")
  public AssignmentDetailView submit(
      Actor actor, UUID assignmentId, SubmitAssignmentCommand command) {
    Assignment assignment = require(assignmentId);
    if (!assignment.getStudentId().equals(actor.id())) {
      throw Rejections.reject(
          log,
          ErrorCode.ACCESS_DENIED,
          "Only the assignment's own student can submit",
          "callerId",
          actor.id(),
          "assignmentId",
          assignmentId);
    }
    assignment.submit(command.description(), command.fileIds(), calendar.now());
    saveAssignment.save(assignment);
    log.atInfo()
        .addKeyValue("assignmentId", assignmentId)
        .addKeyValue("late", assignment.getSubmission().late())
        .log("Assignment submitted");
    return requireView(assignmentId);
  }

  @Override
  @Transactional
  @Observed(name = "assignment.evaluate")
  public AssignmentDetailView evaluate(
      Actor actor, UUID assignmentId, EvaluateAssignmentCommand command) {
    Assignment assignment = require(assignmentId);
    Task task = requireTask(assignment);
    if (!actor.id().equals(task.getTeacherId())) {
      throw Rejections.reject(
          log,
          ErrorCode.NOT_TASK_TEACHER,
          "Only the task's teacher can evaluate",
          "callerId",
          actor.id(),
          "assignmentId",
          assignmentId);
    }
    assignment.evaluate(actor.id(), command.score(), command.feedback(), calendar.now());
    saveAssignment.save(assignment);
    publisher.publish(
        new SubmissionGraded(
            assignmentId,
            task.getId(),
            assignment.getStudentId(),
            task.getBranchId(),
            task.getTitle(),
            assignment.getDueAt(),
            command.score()));
    log.atInfo().addKeyValue("assignmentId", assignmentId).log("Assignment evaluated");
    return requireView(assignmentId);
  }

  @Override
  @Transactional(readOnly = true)
  public AssignmentDetailView get(Actor actor, UUID assignmentId) {
    AssignmentDetailView view = requireView(assignmentId);
    if (!mayView(actor, view)) {
      throw Rejections.reject(
          log,
          ErrorCode.ACCESS_DENIED,
          "Caller may not view this assignment",
          "callerId",
          actor.id(),
          "assignmentId",
          assignmentId);
    }
    return view;
  }

  @Override
  @Transactional(readOnly = true)
  public Page<AssignmentSummaryView> list(
      Actor actor,
      UUID studentId,
      AssignmentStatus status,
      LocalDate from,
      LocalDate to,
      Pageable pageable) {
    AssignmentFilter filter = filterFor(actor, studentId, status, from, to);
    return assignmentQuery.findSummaries(filter, pageable);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<AssignmentSummaryView> listOfTask(
      Actor actor,
      UUID taskId,
      LocalDate occurrenceDate,
      AssignmentStatus status,
      Pageable pageable) {
    taskAccess.requireManager(actor, taskId);
    return assignmentQuery.findSummaries(
        new AssignmentFilter(null, null, null, taskId, status, occurrenceDate, occurrenceDate),
        pageable);
  }

  /** The caller's scope, narrowed by the optional student filter. */
  private AssignmentFilter filterFor(
      Actor actor, UUID studentId, AssignmentStatus status, LocalDate from, LocalDate to) {
    if (actor.isAdmin() || actor.isSuperAdmin()) {
      UUID branch = branchAccess.resolveBranchFilter(actor, null, "assignment.list");
      return new AssignmentFilter(studentId, null, branch, null, status, from, to);
    }
    if (actor.isTeacher()) {
      return new AssignmentFilter(studentId, actor.id(), null, null, status, from, to);
    }
    if (actor.isGuardian()) {
      if (studentId == null) {
        throw Rejections.reject(
            log,
            ErrorCode.STUDENT_FILTER_REQUIRED,
            "A guardian must name the child",
            "callerId",
            actor.id());
      }
      requireGuardianOf(actor, studentId);
      return new AssignmentFilter(studentId, null, null, null, status, from, to);
    }
    if (actor.isStudent()) {
      if (studentId != null && !studentId.equals(actor.id())) {
        throw Rejections.reject(
            log,
            ErrorCode.ACCESS_DENIED,
            "A student lists only their own assignments",
            "callerId",
            actor.id());
      }
      return new AssignmentFilter(actor.id(), null, null, null, status, from, to);
    }
    throw Rejections.reject(
        log,
        ErrorCode.ACCESS_DENIED,
        "Caller has no role that lists assignments",
        "callerId",
        actor.id());
  }

  private void requireGuardianOf(Actor actor, UUID studentId) {
    if (!guardians.isGuardianOf(actor.id(), studentId)) {
      throw Rejections.reject(
          log,
          ErrorCode.GUARDIAN_NOT_LINKED,
          "The student is not linked to the caller",
          "callerId",
          actor.id(),
          "studentId",
          studentId);
    }
  }

  private boolean mayView(Actor actor, AssignmentDetailView view) {
    if (actor.id().equals(view.studentId()) || actor.id().equals(view.teacherId())) {
      return true;
    }
    if (actor.isSuperAdmin()) {
      return true;
    }
    // A missing branch claim never matches: null must not open every branch.
    if (actor.isAdmin() && actor.branchId() != null && actor.branchId().equals(view.branchId())) {
      return true;
    }
    return actor.isGuardian() && guardians.isGuardianOf(actor.id(), view.studentId());
  }

  private Assignment require(UUID assignmentId) {
    return loadAssignment
        .findById(assignmentId)
        .orElseThrow(
            () ->
                Rejections.reject(
                    log,
                    ErrorCode.ASSIGNMENT_NOT_FOUND,
                    "Assignment not found",
                    "assignmentId",
                    assignmentId));
  }

  private Task requireTask(Assignment assignment) {
    return tasks
        .findById(assignment.getTaskId())
        .orElseThrow(
            () ->
                Rejections.reject(
                    log,
                    ErrorCode.TASK_NOT_FOUND,
                    "Task of the assignment not found",
                    "taskId",
                    assignment.getTaskId()));
  }

  private AssignmentDetailView requireView(UUID assignmentId) {
    return assignmentQuery
        .findDetailById(assignmentId)
        .orElseThrow(
            () ->
                Rejections.reject(
                    log,
                    ErrorCode.ASSIGNMENT_NOT_FOUND,
                    "Assignment not found",
                    "assignmentId",
                    assignmentId));
  }
}
