package com.dershane.userservice.user.adapter.in.web;

import com.dershane.userservice.shared.adapter.in.security.SecurityRoles;
import com.dershane.userservice.shared.adapter.in.web.CurrentActor;
import com.dershane.userservice.shared.adapter.in.web.idempotency.Idempotent;
import com.dershane.userservice.shared.application.Actor;
import com.dershane.userservice.user.adapter.in.web.dto.CreateStudentRequest;
import com.dershane.userservice.user.adapter.in.web.dto.StudentResponse;
import com.dershane.userservice.user.adapter.in.web.dto.UpdateStudentRequest;
import com.dershane.userservice.user.adapter.in.web.dto.UserCreatedResponse;
import com.dershane.userservice.user.application.port.in.GetStudentQuery;
import com.dershane.userservice.user.application.port.in.ListStudentsQuery;
import com.dershane.userservice.user.application.port.in.RegisterStudentUseCase;
import com.dershane.userservice.user.application.port.in.UpdateStudentUseCase;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Students")
@RestController
@RequestMapping("/api/v1/students")
@RequiredArgsConstructor
class StudentController {

  private final RegisterStudentUseCase registerStudent;
  private final UpdateStudentUseCase updateStudent;
  private final GetStudentQuery getStudent;
  private final ListStudentsQuery listStudents;
  private final UserWebMapper mapper;

  @Operation(
      summary = "Register a student and open their login account",
      description =
          "With e-mail, Keycloak sends a password setup e-mail. Without e-mail, the username is"
              + " branchcode-studentnumber and a temporary password is returned once.")
  @PostMapping
  @Idempotent
  @PreAuthorize(SecurityRoles.ANY_ADMIN)
  public ResponseEntity<UserCreatedResponse<StudentResponse>> create(
      @CurrentActor Actor actor, @Valid @RequestBody CreateStudentRequest request) {
    UserCreatedResponse<StudentResponse> created =
        UserCreatedResponse.of(
            registerStudent.register(actor, mapper.toCommand(request)), mapper::toResponse);
    return ResponseEntity.created(URI.create("/api/v1/students/" + created.user().id()))
        .body(created);
  }

  @Operation(summary = "List students (admins; filter by branch and active)")
  @GetMapping
  @PreAuthorize(SecurityRoles.ANY_ADMIN)
  public PagedModel<StudentResponse> list(
      @CurrentActor Actor actor,
      @RequestParam(required = false) UUID branchId,
      @RequestParam(required = false) Boolean active,
      Pageable pageable) {
    return new PagedModel<>(
        listStudents.list(actor, branchId, active, pageable).map(mapper::toResponse));
  }

  @Operation(summary = "Get a student (admins, the student, their teachers and guardian)")
  @GetMapping("/{studentId}")
  public StudentResponse get(@CurrentActor Actor actor, @PathVariable UUID studentId) {
    return mapper.toResponse(getStudent.get(actor, studentId));
  }

  @Operation(summary = "Update a student; null fields are left unchanged")
  @PatchMapping("/{studentId}")
  @PreAuthorize(SecurityRoles.ANY_ADMIN)
  public StudentResponse update(
      @CurrentActor Actor actor,
      @PathVariable UUID studentId,
      @Valid @RequestBody UpdateStudentRequest request) {
    return mapper.toResponse(updateStudent.update(actor, studentId, mapper.toCommand(request)));
  }
}
