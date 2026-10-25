package com.dershane.userservice.classroom.adapter.in.web;

import com.dershane.userservice.classroom.adapter.in.web.dto.ClassroomResponse;
import com.dershane.userservice.classroom.adapter.in.web.dto.CreateClassroomRequest;
import com.dershane.userservice.classroom.adapter.in.web.dto.UpdateClassroomRequest;
import com.dershane.userservice.classroom.application.port.in.CreateClassroomUseCase;
import com.dershane.userservice.classroom.application.port.in.DeactivateClassroomUseCase;
import com.dershane.userservice.classroom.application.port.in.GetClassroomQuery;
import com.dershane.userservice.classroom.application.port.in.ListClassroomsQuery;
import com.dershane.userservice.classroom.application.port.in.UpdateClassroomUseCase;
import com.dershane.userservice.shared.adapter.in.security.SecurityRoles;
import com.dershane.userservice.shared.adapter.in.web.CurrentActor;
import com.dershane.userservice.shared.adapter.in.web.idempotency.Idempotent;
import com.dershane.userservice.shared.application.Actor;
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

@Tag(name = "Classrooms")
@RestController
@RequestMapping("/api/v1/classrooms")
@RequiredArgsConstructor
class ClassroomController {

  private final CreateClassroomUseCase createClassroom;
  private final UpdateClassroomUseCase updateClassroom;
  private final DeactivateClassroomUseCase deactivateClassroom;
  private final GetClassroomQuery getClassroom;
  private final ListClassroomsQuery listClassrooms;
  private final ClassroomWebMapper mapper;

  @Operation(summary = "Create a classroom in a branch (admin of that branch)")
  @PostMapping
  @Idempotent
  @PreAuthorize(SecurityRoles.ANY_ADMIN)
  public ResponseEntity<ClassroomResponse> create(
      @CurrentActor Actor actor, @Valid @RequestBody CreateClassroomRequest request) {
    ClassroomResponse created =
        mapper.toResponse(createClassroom.create(actor, mapper.toCommand(request)));
    return ResponseEntity.created(URI.create("/api/v1/classrooms/" + created.id())).body(created);
  }

  @Operation(summary = "List classrooms (admins; branch defaults to the caller's branch)")
  @GetMapping
  @PreAuthorize(SecurityRoles.ANY_ADMIN)
  public PagedModel<ClassroomResponse> list(
      @CurrentActor Actor actor,
      @RequestParam(required = false) UUID branchId,
      @RequestParam(required = false) String term,
      @RequestParam(required = false) Boolean active,
      Pageable pageable) {
    return new PagedModel<>(
        listClassrooms.list(actor, branchId, term, active, pageable).map(mapper::toResponse));
  }

  @Operation(summary = "Get a classroom (admins, its teachers and students)")
  @GetMapping("/{classroomId}")
  public ClassroomResponse get(@CurrentActor Actor actor, @PathVariable UUID classroomId) {
    return mapper.toResponse(getClassroom.get(actor, classroomId));
  }

  @Operation(summary = "Rename a classroom or change its term")
  @PatchMapping("/{classroomId}")
  @PreAuthorize(SecurityRoles.ANY_ADMIN)
  public ClassroomResponse update(
      @CurrentActor Actor actor,
      @PathVariable UUID classroomId,
      @Valid @RequestBody UpdateClassroomRequest request) {
    return mapper.toResponse(updateClassroom.update(actor, classroomId, mapper.toCommand(request)));
  }

  @Operation(summary = "Deactivate a classroom; fails while students are enrolled")
  @PostMapping("/{classroomId}/deactivate")
  @PreAuthorize(SecurityRoles.ANY_ADMIN)
  public ClassroomResponse deactivate(@CurrentActor Actor actor, @PathVariable UUID classroomId) {
    return mapper.toResponse(deactivateClassroom.deactivate(actor, classroomId));
  }
}
