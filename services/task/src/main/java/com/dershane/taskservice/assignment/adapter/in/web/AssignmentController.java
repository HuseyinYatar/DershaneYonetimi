package com.dershane.taskservice.assignment.adapter.in.web;

import com.dershane.taskservice.assignment.adapter.in.web.dto.AssignmentDetailResponse;
import com.dershane.taskservice.assignment.adapter.in.web.dto.AssignmentSummaryResponse;
import com.dershane.taskservice.assignment.adapter.in.web.dto.EvaluateAssignmentRequest;
import com.dershane.taskservice.assignment.adapter.in.web.dto.SubmitAssignmentRequest;
import com.dershane.taskservice.assignment.application.port.in.EvaluateAssignmentUseCase;
import com.dershane.taskservice.assignment.application.port.in.GetAssignmentQuery;
import com.dershane.taskservice.assignment.application.port.in.ListAssignmentsQuery;
import com.dershane.taskservice.assignment.application.port.in.SubmitAssignmentUseCase;
import com.dershane.taskservice.assignment.domain.AssignmentStatus;
import com.dershane.taskservice.shared.adapter.in.security.SecurityRoles;
import com.dershane.taskservice.shared.adapter.in.web.CurrentActor;
import com.dershane.taskservice.shared.application.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The path is {@code /task-assignments}, not {@code /assignments}, so it cannot be mistaken for the
 * user service's {@code /teachers/{id}/assignments}.
 */
@Tag(name = "Assignments")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
class AssignmentController {

  private final ListAssignmentsQuery listAssignments;
  private final GetAssignmentQuery getAssignment;
  private final SubmitAssignmentUseCase submitAssignment;
  private final EvaluateAssignmentUseCase evaluateAssignment;
  private final AssignmentWebMapper mapper;

  @Operation(
      summary =
          "List assignments (students: their own; guardians: their child's, studentId required;"
              + " teachers: their tasks'; admins: their branch)")
  @GetMapping("/task-assignments")
  @PreAuthorize(SecurityRoles.ANY_RECORD_VIEWER)
  public PagedModel<AssignmentSummaryResponse> list(
      @CurrentActor Actor actor,
      @RequestParam(required = false) UUID studentId,
      @RequestParam(required = false) AssignmentStatus status,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      Pageable pageable) {
    return new PagedModel<>(
        listAssignments.list(actor, studentId, status, from, to, pageable).map(mapper::toResponse));
  }

  @Operation(summary = "Get an assignment with its submission and evaluation")
  @GetMapping("/task-assignments/{assignmentId}")
  @PreAuthorize(SecurityRoles.ANY_RECORD_VIEWER)
  public AssignmentDetailResponse get(@CurrentActor Actor actor, @PathVariable UUID assignmentId) {
    return mapper.toResponse(getAssignment.get(actor, assignmentId));
  }

  @Operation(summary = "Submit (or replace the submission of) an assignment (the student)")
  @PutMapping("/task-assignments/{assignmentId}/submission")
  @PreAuthorize(SecurityRoles.STUDENT)
  public AssignmentDetailResponse submit(
      @CurrentActor Actor actor,
      @PathVariable UUID assignmentId,
      @Valid @RequestBody SubmitAssignmentRequest request) {
    return mapper.toResponse(
        submitAssignment.submit(actor, assignmentId, mapper.toCommand(request)));
  }

  @Operation(summary = "Evaluate (or re-evaluate) a submission (the task's teacher)")
  @PutMapping("/task-assignments/{assignmentId}/evaluation")
  @PreAuthorize(SecurityRoles.TEACHER)
  public AssignmentDetailResponse evaluate(
      @CurrentActor Actor actor,
      @PathVariable UUID assignmentId,
      @Valid @RequestBody EvaluateAssignmentRequest request) {
    return mapper.toResponse(
        evaluateAssignment.evaluate(actor, assignmentId, mapper.toCommand(request)));
  }

  @Operation(summary = "List the assignments of one task (its teacher or a branch admin)")
  @GetMapping("/tasks/{taskId}/assignments")
  @PreAuthorize(SecurityRoles.ANY_STAFF)
  public PagedModel<AssignmentSummaryResponse> listOfTask(
      @CurrentActor Actor actor,
      @PathVariable UUID taskId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate occurrenceDate,
      @RequestParam(required = false) AssignmentStatus status,
      Pageable pageable) {
    return new PagedModel<>(
        listAssignments
            .listOfTask(actor, taskId, occurrenceDate, status, pageable)
            .map(mapper::toResponse));
  }
}
