package com.dershane.attendanceservice.excuse.adapter.in.web;

import com.dershane.attendanceservice.excuse.adapter.in.web.dto.DecideExcuseRequest;
import com.dershane.attendanceservice.excuse.adapter.in.web.dto.ExcuseResponse;
import com.dershane.attendanceservice.excuse.adapter.in.web.dto.RequestExcuseRequest;
import com.dershane.attendanceservice.excuse.application.port.in.DecideExcuseUseCase;
import com.dershane.attendanceservice.excuse.application.port.in.ListExcusesQuery;
import com.dershane.attendanceservice.excuse.application.port.in.RequestExcuseUseCase;
import com.dershane.attendanceservice.excuse.domain.ExcuseStatus;
import com.dershane.attendanceservice.shared.adapter.in.security.SecurityRoles;
import com.dershane.attendanceservice.shared.adapter.in.web.CurrentActor;
import com.dershane.attendanceservice.shared.adapter.in.web.idempotency.Idempotent;
import com.dershane.attendanceservice.shared.application.Actor;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Excuses")
@RestController
@RequestMapping("/api/v1/excuses")
@RequiredArgsConstructor
class ExcuseController {

  private final RequestExcuseUseCase requestExcuse;
  private final DecideExcuseUseCase decideExcuse;
  private final ListExcusesQuery listExcuses;
  private final ExcuseWebMapper mapper;

  @Operation(summary = "Report an excuse for the guardian's own child")
  @PostMapping
  @Idempotent
  @PreAuthorize(SecurityRoles.GUARDIAN)
  public ResponseEntity<ExcuseResponse> request(
      @CurrentActor Actor actor, @Valid @RequestBody RequestExcuseRequest request) {
    ExcuseResponse created =
        mapper.toResponse(requestExcuse.request(actor, mapper.toCommand(request)));
    return ResponseEntity.created(URI.create("/api/v1/excuses/" + created.id())).body(created);
  }

  @Operation(summary = "List excuses (admins: their branch; guardians: the ones they reported)")
  @GetMapping
  @PreAuthorize("hasAnyRole('GUARDIAN', 'ADMIN', 'SUPER_ADMIN')")
  public PagedModel<ExcuseResponse> list(
      @CurrentActor Actor actor,
      @RequestParam(required = false) ExcuseStatus status,
      @RequestParam(required = false) UUID studentId,
      Pageable pageable) {
    return new PagedModel<>(
        listExcuses.list(actor, status, studentId, pageable).map(mapper::toResponse));
  }

  @Operation(summary = "Approve an excuse; absences in its range become excused (admin)")
  @PostMapping("/{excuseId}/approve")
  @PreAuthorize(SecurityRoles.ANY_ADMIN)
  public ExcuseResponse approve(
      @CurrentActor Actor actor,
      @PathVariable UUID excuseId,
      @Valid @RequestBody(required = false) DecideExcuseRequest request) {
    return mapper.toResponse(decideExcuse.approve(actor, excuseId, noteOf(request)));
  }

  @Operation(summary = "Reject an excuse (admin)")
  @PostMapping("/{excuseId}/reject")
  @PreAuthorize(SecurityRoles.ANY_ADMIN)
  public ExcuseResponse reject(
      @CurrentActor Actor actor,
      @PathVariable UUID excuseId,
      @Valid @RequestBody(required = false) DecideExcuseRequest request) {
    return mapper.toResponse(decideExcuse.reject(actor, excuseId, noteOf(request)));
  }

  private static String noteOf(DecideExcuseRequest request) {
    return request == null ? null : request.note();
  }
}
