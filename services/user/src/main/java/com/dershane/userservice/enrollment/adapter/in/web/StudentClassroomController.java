package com.dershane.userservice.enrollment.adapter.in.web;

import com.dershane.userservice.enrollment.adapter.in.web.dto.AssignClassroomRequest;
import com.dershane.userservice.enrollment.adapter.in.web.dto.ClassroomMembershipResponse;
import com.dershane.userservice.enrollment.application.port.in.AssignStudentToClassroomUseCase;
import com.dershane.userservice.enrollment.application.port.in.RemoveStudentFromClassroomUseCase;
import com.dershane.userservice.shared.adapter.in.security.SecurityRoles;
import com.dershane.userservice.shared.adapter.in.web.CurrentActor;
import com.dershane.userservice.shared.application.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Students")
@RestController
@RequestMapping("/api/v1/students/{studentId}/classroom")
@RequiredArgsConstructor
class StudentClassroomController {

  private final AssignStudentToClassroomUseCase assignStudent;
  private final RemoveStudentFromClassroomUseCase removeStudent;
  private final EnrollmentWebMapper mapper;

  @Operation(
      summary = "Put a student into a classroom",
      description =
          "If the student is in another classroom, that membership is closed first. Repeating the"
              + " call with the same classroom changes nothing.")
  @PutMapping
  @PreAuthorize(SecurityRoles.ANY_ADMIN)
  public ClassroomMembershipResponse assign(
      @CurrentActor Actor actor,
      @PathVariable UUID studentId,
      @Valid @RequestBody AssignClassroomRequest request) {
    return mapper.toResponse(assignStudent.assign(actor, studentId, mapper.toCommand(request)));
  }

  @Operation(summary = "Remove a student from their current classroom")
  @DeleteMapping
  @PreAuthorize(SecurityRoles.ANY_ADMIN)
  public ClassroomMembershipResponse remove(
      @CurrentActor Actor actor, @PathVariable UUID studentId) {
    return mapper.toResponse(removeStudent.remove(actor, studentId));
  }
}
