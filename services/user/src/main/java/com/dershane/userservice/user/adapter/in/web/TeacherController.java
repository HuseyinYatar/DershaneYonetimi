package com.dershane.userservice.user.adapter.in.web;

import com.dershane.userservice.shared.adapter.in.security.SecurityRoles;
import com.dershane.userservice.shared.adapter.in.web.CurrentActor;
import com.dershane.userservice.shared.adapter.in.web.idempotency.Idempotent;
import com.dershane.userservice.shared.application.Actor;
import com.dershane.userservice.user.adapter.in.web.dto.CreateTeacherRequest;
import com.dershane.userservice.user.adapter.in.web.dto.TeacherResponse;
import com.dershane.userservice.user.adapter.in.web.dto.UpdateTeacherRequest;
import com.dershane.userservice.user.adapter.in.web.dto.UserCreatedResponse;
import com.dershane.userservice.user.application.port.in.GetTeacherQuery;
import com.dershane.userservice.user.application.port.in.ListTeachersQuery;
import com.dershane.userservice.user.application.port.in.RegisterTeacherUseCase;
import com.dershane.userservice.user.application.port.in.UpdateTeacherUseCase;
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

@Tag(name = "Teachers")
@RestController
@RequestMapping("/api/v1/teachers")
@RequiredArgsConstructor
class TeacherController {

  private final RegisterTeacherUseCase registerTeacher;
  private final UpdateTeacherUseCase updateTeacher;
  private final GetTeacherQuery getTeacher;
  private final ListTeachersQuery listTeachers;
  private final UserWebMapper mapper;

  @Operation(summary = "Register a teacher (e-mail required) and open their login account")
  @PostMapping
  @Idempotent
  @PreAuthorize(SecurityRoles.ANY_ADMIN)
  public ResponseEntity<UserCreatedResponse<TeacherResponse>> create(
      @CurrentActor Actor actor, @Valid @RequestBody CreateTeacherRequest request) {
    UserCreatedResponse<TeacherResponse> created =
        UserCreatedResponse.of(
            registerTeacher.register(actor, mapper.toCommand(request)), mapper::toResponse);
    return ResponseEntity.created(URI.create("/api/v1/teachers/" + created.user().id()))
        .body(created);
  }

  @Operation(summary = "List teachers (admins)")
  @GetMapping
  @PreAuthorize(SecurityRoles.ANY_ADMIN)
  public PagedModel<TeacherResponse> list(
      @CurrentActor Actor actor,
      @RequestParam(required = false) UUID branchId,
      @RequestParam(required = false) Boolean active,
      Pageable pageable) {
    return new PagedModel<>(
        listTeachers.list(actor, branchId, active, pageable).map(mapper::toResponse));
  }

  @Operation(summary = "Get a teacher")
  @GetMapping("/{teacherId}")
  public TeacherResponse get(@CurrentActor Actor actor, @PathVariable UUID teacherId) {
    return mapper.toResponse(getTeacher.get(actor, teacherId));
  }

  @Operation(summary = "Update a teacher; null fields are left unchanged")
  @PatchMapping("/{teacherId}")
  @PreAuthorize(SecurityRoles.ANY_ADMIN)
  public TeacherResponse update(
      @CurrentActor Actor actor,
      @PathVariable UUID teacherId,
      @Valid @RequestBody UpdateTeacherRequest request) {
    return mapper.toResponse(updateTeacher.update(actor, teacherId, mapper.toCommand(request)));
  }
}
