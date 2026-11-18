package com.dershane.userservice.enrollment.adapter.in.web;

import com.dershane.userservice.enrollment.adapter.in.web.dto.BranchTeacherAssignmentResponse;
import com.dershane.userservice.enrollment.application.port.in.ListBranchTeacherAssignmentsQuery;
import com.dershane.userservice.shared.adapter.in.security.SecurityRoles;
import com.dershane.userservice.shared.adapter.in.web.CurrentActor;
import com.dershane.userservice.shared.application.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Teachers")
@RestController
@RequestMapping("/api/v1/branches/{branchId}/teacher-assignments")
@RequiredArgsConstructor
class BranchTeacherAssignmentController {

  private final ListBranchTeacherAssignmentsQuery listAssignments;
  private final EnrollmentWebMapper mapper;

  @Operation(summary = "Teacher assignments of a branch's active classrooms, with weekly hours")
  @GetMapping
  @PreAuthorize(SecurityRoles.ANY_ADMIN)
  public List<BranchTeacherAssignmentResponse> list(
      @CurrentActor Actor actor, @PathVariable UUID branchId) {
    return listAssignments.listOfBranch(actor, branchId).stream().map(mapper::toResponse).toList();
  }
}
