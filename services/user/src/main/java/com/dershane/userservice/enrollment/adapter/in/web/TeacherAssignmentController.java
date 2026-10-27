package com.dershane.userservice.enrollment.adapter.in.web;

import com.dershane.userservice.enrollment.adapter.in.web.dto.CreateTeacherAssignmentRequest;
import com.dershane.userservice.enrollment.adapter.in.web.dto.TeacherAssignmentResponse;
import com.dershane.userservice.enrollment.application.port.in.AssignTeacherUseCase;
import com.dershane.userservice.enrollment.application.port.in.ListTeacherAssignmentsQuery;
import com.dershane.userservice.enrollment.application.port.in.RemoveTeacherAssignmentUseCase;
import com.dershane.userservice.shared.adapter.in.security.SecurityRoles;
import com.dershane.userservice.shared.adapter.in.web.CurrentActor;
import com.dershane.userservice.shared.adapter.in.web.idempotency.Idempotent;
import com.dershane.userservice.shared.application.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Teachers")
@RestController
@RequestMapping("/api/v1/teachers/{teacherId}/assignments")
@RequiredArgsConstructor
class TeacherAssignmentController {

  private final AssignTeacherUseCase assignTeacher;
  private final RemoveTeacherAssignmentUseCase removeAssignment;
  private final ListTeacherAssignmentsQuery listAssignments;
  private final EnrollmentWebMapper mapper;

  @Operation(summary = "Assign a teacher to teach a subject in a classroom")
  @PostMapping
  @Idempotent
  @PreAuthorize(SecurityRoles.ANY_ADMIN)
  public ResponseEntity<TeacherAssignmentResponse> create(
      @CurrentActor Actor actor,
      @PathVariable UUID teacherId,
      @Valid @RequestBody CreateTeacherAssignmentRequest request) {
    TeacherAssignmentResponse created =
        mapper.toResponse(assignTeacher.assign(actor, teacherId, mapper.toCommand(request)));
    return ResponseEntity.created(
            URI.create("/api/v1/teachers/" + teacherId + "/assignments/" + created.id()))
        .body(created);
  }

  @Operation(summary = "List a teacher's assignments (admins or the teacher)")
  @GetMapping
  public List<TeacherAssignmentResponse> list(
      @CurrentActor Actor actor, @PathVariable UUID teacherId) {
    return listAssignments.list(actor, teacherId).stream().map(mapper::toResponse).toList();
  }

  @Operation(summary = "Remove an assignment")
  @DeleteMapping("/{assignmentId}")
  @PreAuthorize(SecurityRoles.ANY_ADMIN)
  public ResponseEntity<Void> delete(
      @CurrentActor Actor actor, @PathVariable UUID teacherId, @PathVariable UUID assignmentId) {
    removeAssignment.remove(actor, teacherId, assignmentId);
    return ResponseEntity.noContent().build();
  }
}
