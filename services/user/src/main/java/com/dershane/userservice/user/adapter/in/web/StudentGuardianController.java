package com.dershane.userservice.user.adapter.in.web;

import com.dershane.userservice.shared.adapter.in.security.SecurityRoles;
import com.dershane.userservice.shared.adapter.in.web.CurrentActor;
import com.dershane.userservice.shared.application.Actor;
import com.dershane.userservice.user.adapter.in.web.dto.LinkGuardianRequest;
import com.dershane.userservice.user.adapter.in.web.dto.StudentGuardianResponse;
import com.dershane.userservice.user.application.port.in.GetStudentGuardianQuery;
import com.dershane.userservice.user.application.port.in.LinkGuardianUseCase;
import com.dershane.userservice.user.application.port.in.UnlinkGuardianUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Students")
@RestController
@RequestMapping("/api/v1/students/{studentId}/guardian")
@RequiredArgsConstructor
class StudentGuardianController {

  private final LinkGuardianUseCase linkGuardian;
  private final UnlinkGuardianUseCase unlinkGuardian;
  private final GetStudentGuardianQuery getStudentGuardian;
  private final UserWebMapper mapper;

  @Operation(
      summary = "Link a guardian to a student",
      description = "A student has one guardian; linking another guardian replaces the old link.")
  @PutMapping
  @PreAuthorize(SecurityRoles.ANY_ADMIN)
  public StudentGuardianResponse link(
      @CurrentActor Actor actor,
      @PathVariable UUID studentId,
      @Valid @RequestBody LinkGuardianRequest request) {
    return mapper.toResponse(linkGuardian.link(actor, studentId, mapper.toCommand(request)));
  }

  @Operation(summary = "The student's guardian")
  @GetMapping
  public StudentGuardianResponse get(@CurrentActor Actor actor, @PathVariable UUID studentId) {
    return mapper.toResponse(getStudentGuardian.guardianOf(actor, studentId));
  }

  @Operation(summary = "Remove the student's guardian link")
  @DeleteMapping
  @PreAuthorize(SecurityRoles.ANY_ADMIN)
  public ResponseEntity<Void> unlink(@CurrentActor Actor actor, @PathVariable UUID studentId) {
    unlinkGuardian.unlink(actor, studentId);
    return ResponseEntity.noContent().build();
  }
}
