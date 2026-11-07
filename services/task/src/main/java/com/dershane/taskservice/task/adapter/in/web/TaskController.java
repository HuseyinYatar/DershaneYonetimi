package com.dershane.taskservice.task.adapter.in.web;

import com.dershane.taskservice.shared.adapter.in.security.SecurityRoles;
import com.dershane.taskservice.shared.adapter.in.web.CurrentActor;
import com.dershane.taskservice.shared.adapter.in.web.idempotency.Idempotent;
import com.dershane.taskservice.shared.application.Actor;
import com.dershane.taskservice.task.adapter.in.web.dto.CreateTaskRequest;
import com.dershane.taskservice.task.adapter.in.web.dto.TaskResponse;
import com.dershane.taskservice.task.adapter.in.web.dto.UpdateTaskRequest;
import com.dershane.taskservice.task.application.port.in.CreateTaskUseCase;
import com.dershane.taskservice.task.application.port.in.DeactivateTaskUseCase;
import com.dershane.taskservice.task.application.port.in.GetTaskQuery;
import com.dershane.taskservice.task.application.port.in.ListTasksQuery;
import com.dershane.taskservice.task.application.port.in.UpdateTaskUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Tasks")
@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
class TaskController {

  private final CreateTaskUseCase createTask;
  private final UpdateTaskUseCase updateTask;
  private final DeactivateTaskUseCase deactivateTask;
  private final GetTaskQuery getTask;
  private final ListTasksQuery listTasks;
  private final TaskWebMapper mapper;

  @Operation(summary = "Give a task to a student or a classroom (teacher)")
  @PostMapping
  @Idempotent
  @PreAuthorize(SecurityRoles.TEACHER)
  public ResponseEntity<TaskResponse> create(
      @CurrentActor Actor actor, @Valid @RequestBody CreateTaskRequest request) {
    TaskResponse created = mapper.toResponse(createTask.create(actor, mapper.toCommand(request)));
    return ResponseEntity.created(URI.create("/api/v1/tasks/" + created.id())).body(created);
  }

  @Operation(summary = "List tasks (teachers: their own; admins: their branch)")
  @GetMapping
  @PreAuthorize(SecurityRoles.ANY_STAFF)
  public PagedModel<TaskResponse> list(
      @CurrentActor Actor actor,
      @RequestParam(required = false) UUID teacherId,
      @RequestParam(required = false) UUID branchId,
      @RequestParam(required = false) Boolean active,
      Pageable pageable) {
    return new PagedModel<>(
        listTasks.list(actor, teacherId, branchId, active, pageable).map(mapper::toResponse));
  }

  @Operation(summary = "Get a task (its teacher or a branch admin)")
  @GetMapping("/{taskId}")
  @PreAuthorize(SecurityRoles.ANY_STAFF)
  public TaskResponse get(@CurrentActor Actor actor, @PathVariable UUID taskId) {
    return mapper.toResponse(getTask.get(actor, taskId));
  }

  @Operation(summary = "Edit the texts and attachments of a task")
  @PutMapping("/{taskId}")
  @PreAuthorize(SecurityRoles.ANY_STAFF)
  public TaskResponse update(
      @CurrentActor Actor actor,
      @PathVariable UUID taskId,
      @Valid @RequestBody UpdateTaskRequest request) {
    return mapper.toResponse(updateTask.update(actor, taskId, mapper.toCommand(request)));
  }

  @Operation(summary = "Stop handing out new assignments of a task")
  @PostMapping("/{taskId}/deactivate")
  @PreAuthorize(SecurityRoles.ANY_STAFF)
  public TaskResponse deactivate(@CurrentActor Actor actor, @PathVariable UUID taskId) {
    return mapper.toResponse(deactivateTask.deactivate(actor, taskId));
  }
}
