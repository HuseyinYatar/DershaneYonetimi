package com.dershane.taskservice.task.application.service;

import com.dershane.taskservice.roster.application.port.in.ActiveMembershipView;
import com.dershane.taskservice.roster.application.port.in.MembershipQuery;
import com.dershane.taskservice.shared.application.Actor;
import com.dershane.taskservice.shared.application.BranchAccessPolicy;
import com.dershane.taskservice.shared.application.BusinessCalendar;
import com.dershane.taskservice.shared.application.TaskProperties;
import com.dershane.taskservice.shared.domain.ErrorCode;
import com.dershane.taskservice.shared.domain.Rejections;
import com.dershane.taskservice.task.application.event.TaskCreated;
import com.dershane.taskservice.task.application.port.in.CreateTaskCommand;
import com.dershane.taskservice.task.application.port.in.CreateTaskUseCase;
import com.dershane.taskservice.task.application.port.in.DeactivateTaskUseCase;
import com.dershane.taskservice.task.application.port.in.GetTaskQuery;
import com.dershane.taskservice.task.application.port.in.ListTasksQuery;
import com.dershane.taskservice.task.application.port.in.TaskView;
import com.dershane.taskservice.task.application.port.in.UpdateTaskCommand;
import com.dershane.taskservice.task.application.port.in.UpdateTaskUseCase;
import com.dershane.taskservice.task.application.port.out.LoadTaskPort;
import com.dershane.taskservice.task.application.port.out.SaveTaskPort;
import com.dershane.taskservice.task.application.port.out.TaskQueryPort;
import com.dershane.taskservice.task.domain.TargetType;
import com.dershane.taskservice.task.domain.Task;
import com.dershane.taskservice.task.domain.TaskDraft;
import io.micrometer.observation.annotation.Observed;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
class TaskService
    implements CreateTaskUseCase,
        UpdateTaskUseCase,
        DeactivateTaskUseCase,
        GetTaskQuery,
        ListTasksQuery {

  private final LoadTaskPort loadTask;
  private final SaveTaskPort saveTask;
  private final TaskQueryPort taskQuery;
  private final MembershipQuery memberships;
  private final BranchAccessPolicy branchAccess;
  private final BusinessCalendar calendar;
  private final ApplicationEventPublisher applicationEvents;
  private final TaskProperties properties;

  @Override
  @Transactional
  @Observed(name = "task.create")
  public TaskView create(Actor actor, CreateTaskCommand command) {
    if (!actor.isTeacher()) {
      throw Rejections.reject(
          log, ErrorCode.ACCESS_DENIED, "Only a teacher can create a task", "callerId", actor.id());
    }
    if (actor.branchId() == null) {
      throw Rejections.reject(
          log,
          ErrorCode.ACCESS_DENIED,
          "Task rejected: the teacher's token carries no branch",
          "callerId",
          actor.id());
    }
    requireAttachmentLimit(command.attachmentFileIds().size(), actor.id());
    LocalDate today = calendar.today();
    requireTargetInBranch(actor, command.targetType(), command.targetId(), today);
    Task task =
        Task.create(
            new TaskDraft(
                actor.branchId(),
                actor.id(),
                command.title(),
                command.description(),
                command.targetType(),
                command.targetId(),
                command.recurrence(),
                command.recurrenceDays(),
                command.startDate(),
                command.endDate(),
                command.dueTime(),
                command.attachmentFileIds()),
            today,
            calendar.now(),
            properties.timeZone());
    Task saved = saveTask.save(task);
    applicationEvents.publishEvent(new TaskCreated(saved.getId()));
    log.atInfo()
        .addKeyValue("taskId", saved.getId())
        .addKeyValue("teacherId", actor.id())
        .addKeyValue("targetType", saved.getTargetType())
        .addKeyValue("targetId", saved.getTargetId())
        .addKeyValue("recurrence", saved.getRecurrence())
        .log("Task created");
    return requireView(saved.getId());
  }

  @Override
  @Transactional
  @Observed(name = "task.update")
  public TaskView update(Actor actor, UUID taskId, UpdateTaskCommand command) {
    Task task = require(taskId);
    requireManager(actor, task, "task.update");
    requireAttachmentLimit(command.attachmentFileIds().size(), actor.id());
    task.edit(command.title(), command.description(), command.attachmentFileIds());
    saveTask.save(task);
    log.atInfo().addKeyValue("taskId", taskId).log("Task updated");
    return requireView(taskId);
  }

  @Override
  @Transactional
  @Observed(name = "task.deactivate")
  public TaskView deactivate(Actor actor, UUID taskId) {
    Task task = require(taskId);
    requireManager(actor, task, "task.deactivate");
    task.deactivate();
    saveTask.save(task);
    log.atInfo().addKeyValue("taskId", taskId).log("Task deactivated");
    return requireView(taskId);
  }

  @Override
  @Transactional(readOnly = true)
  public TaskView get(Actor actor, UUID taskId) {
    requireManager(actor, require(taskId), "task.get");
    return requireView(taskId);
  }

  @Override
  @Transactional(readOnly = true)
  public void requireManager(Actor actor, UUID taskId) {
    requireManager(actor, require(taskId), "task.assignments.list");
  }

  @Override
  @Transactional(readOnly = true)
  public Page<TaskView> list(
      Actor actor, UUID teacherId, UUID branchId, Boolean active, Pageable pageable) {
    if (actor.isTeacher() && !actor.isAdmin() && !actor.isSuperAdmin()) {
      if (teacherId != null && !teacherId.equals(actor.id())) {
        throw Rejections.reject(
            log,
            ErrorCode.ACCESS_DENIED,
            "A teacher lists only their own tasks",
            "callerId",
            actor.id(),
            "requestedTeacherId",
            teacherId);
      }
      return taskQuery.findViews(null, actor.id(), active, pageable);
    }
    if (actor.isAdmin() || actor.isSuperAdmin()) {
      UUID branchFilter = branchAccess.resolveBranchFilter(actor, branchId, "task.list");
      return taskQuery.findViews(branchFilter, teacherId, active, pageable);
    }
    throw Rejections.reject(
        log,
        ErrorCode.ACCESS_DENIED,
        "Only teachers and admins list tasks",
        "callerId",
        actor.id());
  }

  private void requireAttachmentLimit(int count, UUID callerId) {
    if (count > properties.maxAttachments()) {
      throw Rejections.reject(
          log,
          ErrorCode.TOO_MANY_ATTACHMENTS,
          "Task rejected: too many attachments",
          "callerId",
          callerId,
          "count",
          count,
          "max",
          properties.maxAttachments());
    }
  }

  /** Branch check only: the teacher-classroom relation is not verified (known limit). */
  private void requireTargetInBranch(
      Actor actor, TargetType targetType, UUID targetId, LocalDate today) {
    boolean inBranch =
        switch (targetType) {
          case CLASSROOM ->
              memberships
                  .branchOfClassroomOn(targetId, today)
                  .filter(actor.branchId()::equals)
                  .isPresent();
          case STUDENT ->
              memberships
                  .activeMembershipOf(targetId, today)
                  .map(ActiveMembershipView::branchId)
                  .filter(actor.branchId()::equals)
                  .isPresent();
          default -> false;
        };
    if (!inBranch) {
      throw Rejections.reject(
          log,
          ErrorCode.TARGET_NOT_IN_BRANCH,
          "Task rejected: the target has no active membership in the teacher's branch",
          "callerId",
          actor.id(),
          "targetType",
          targetType,
          "targetId",
          targetId);
    }
  }

  /** The task's teacher, or an admin of the task's branch (super admins: any branch). */
  private void requireManager(Actor actor, Task task, String action) {
    if (actor.isSuperAdmin() || actor.isAdmin()) {
      branchAccess.requireBranchAdmin(actor, task.getBranchId(), action);
      return;
    }
    if (actor.isTeacher() && actor.id().equals(task.getTeacherId())) {
      return;
    }
    throw Rejections.reject(
        log,
        ErrorCode.NOT_TASK_TEACHER,
        "Caller is neither the task's teacher nor an admin of its branch",
        "callerId",
        actor.id(),
        "taskId",
        task.getId(),
        "action",
        action);
  }

  private Task require(UUID taskId) {
    return loadTask
        .findById(taskId)
        .orElseThrow(
            () ->
                Rejections.reject(
                    log, ErrorCode.TASK_NOT_FOUND, "Task not found", "taskId", taskId));
  }

  private TaskView requireView(UUID taskId) {
    return taskQuery
        .findViewById(taskId)
        .orElseThrow(
            () ->
                Rejections.reject(
                    log, ErrorCode.TASK_NOT_FOUND, "Task not found", "taskId", taskId));
  }
}
