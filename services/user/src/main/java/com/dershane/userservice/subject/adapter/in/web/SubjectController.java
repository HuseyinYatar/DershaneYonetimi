package com.dershane.userservice.subject.adapter.in.web;

import com.dershane.userservice.shared.adapter.in.security.SecurityRoles;
import com.dershane.userservice.shared.adapter.in.web.idempotency.Idempotent;
import com.dershane.userservice.subject.adapter.in.web.dto.CreateSubjectRequest;
import com.dershane.userservice.subject.adapter.in.web.dto.SubjectResponse;
import com.dershane.userservice.subject.adapter.in.web.dto.UpdateSubjectRequest;
import com.dershane.userservice.subject.application.port.in.CreateSubjectUseCase;
import com.dershane.userservice.subject.application.port.in.DeactivateSubjectUseCase;
import com.dershane.userservice.subject.application.port.in.GetSubjectQuery;
import com.dershane.userservice.subject.application.port.in.ListSubjectsQuery;
import com.dershane.userservice.subject.application.port.in.UpdateSubjectUseCase;
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

@Tag(name = "Subjects")
@RestController
@RequestMapping("/api/v1/subjects")
@RequiredArgsConstructor
class SubjectController {

  private final CreateSubjectUseCase createSubject;
  private final UpdateSubjectUseCase updateSubject;
  private final DeactivateSubjectUseCase deactivateSubject;
  private final GetSubjectQuery getSubject;
  private final ListSubjectsQuery listSubjects;
  private final SubjectWebMapper mapper;

  @Operation(summary = "Create a subject (super admin)")
  @PostMapping
  @Idempotent
  @PreAuthorize(SecurityRoles.ONLY_SUPER_ADMIN)
  public ResponseEntity<SubjectResponse> create(@Valid @RequestBody CreateSubjectRequest request) {
    SubjectResponse created = mapper.toResponse(createSubject.create(mapper.toCommand(request)));
    return ResponseEntity.created(URI.create("/api/v1/subjects/" + created.id())).body(created);
  }

  @Operation(summary = "List subjects (any authenticated user)")
  @GetMapping
  public PagedModel<SubjectResponse> list(
      @RequestParam(required = false) Boolean active, Pageable pageable) {
    return new PagedModel<>(listSubjects.list(active, pageable).map(mapper::toResponse));
  }

  @Operation(summary = "Get a subject")
  @GetMapping("/{subjectId}")
  public SubjectResponse get(@PathVariable UUID subjectId) {
    return mapper.toResponse(getSubject.get(subjectId));
  }

  @Operation(summary = "Rename a subject (super admin)")
  @PatchMapping("/{subjectId}")
  @PreAuthorize(SecurityRoles.ONLY_SUPER_ADMIN)
  public SubjectResponse update(
      @PathVariable UUID subjectId, @Valid @RequestBody UpdateSubjectRequest request) {
    return mapper.toResponse(updateSubject.update(subjectId, mapper.toCommand(request)));
  }

  @Operation(summary = "Deactivate a subject (super admin)")
  @PostMapping("/{subjectId}/deactivate")
  @PreAuthorize(SecurityRoles.ONLY_SUPER_ADMIN)
  public SubjectResponse deactivate(@PathVariable UUID subjectId) {
    return mapper.toResponse(deactivateSubject.deactivate(subjectId));
  }
}
