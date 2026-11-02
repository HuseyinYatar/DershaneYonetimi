package com.dershane.attendanceservice.session.adapter.in.web;

import com.dershane.attendanceservice.session.adapter.in.web.dto.CreateSessionRequest;
import com.dershane.attendanceservice.session.adapter.in.web.dto.ReassignTeacherRequest;
import com.dershane.attendanceservice.session.adapter.in.web.dto.SessionResponse;
import com.dershane.attendanceservice.session.application.port.in.CancelSessionUseCase;
import com.dershane.attendanceservice.session.application.port.in.CreateAdHocSessionUseCase;
import com.dershane.attendanceservice.session.application.port.in.GetSessionQuery;
import com.dershane.attendanceservice.session.application.port.in.ListSessionsQuery;
import com.dershane.attendanceservice.session.application.port.in.ReassignTeacherUseCase;
import com.dershane.attendanceservice.session.domain.SessionStatus;
import com.dershane.attendanceservice.shared.adapter.in.security.SecurityRoles;
import com.dershane.attendanceservice.shared.adapter.in.web.CurrentActor;
import com.dershane.attendanceservice.shared.adapter.in.web.idempotency.Idempotent;
import com.dershane.attendanceservice.shared.application.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Class sessions")
@RestController
@RequestMapping("/api/v1/class-sessions")
@RequiredArgsConstructor
class SessionController {

  private final CreateAdHocSessionUseCase createSession;
  private final CancelSessionUseCase cancelSession;
  private final ReassignTeacherUseCase reassignTeacher;
  private final GetSessionQuery getSession;
  private final ListSessionsQuery listSessions;
  private final SessionWebMapper mapper;

  @Operation(summary = "Open a session outside any weekly schedule (admin of the branch)")
  @PostMapping
  @Idempotent
  @PreAuthorize(SecurityRoles.ANY_ADMIN)
  public ResponseEntity<SessionResponse> create(
      @CurrentActor Actor actor, @Valid @RequestBody CreateSessionRequest request) {
    SessionResponse created =
        mapper.toResponse(createSession.create(actor, mapper.toCommand(request)));
    return ResponseEntity.created(URI.create("/api/v1/class-sessions/" + created.id()))
        .body(created);
  }

  @Operation(summary = "List sessions (admins: their branch; teachers: only their own sessions)")
  @GetMapping
  @PreAuthorize(SecurityRoles.ANY_STAFF)
  public PagedModel<SessionResponse> list(
      @CurrentActor Actor actor,
      @RequestParam(required = false) UUID branchId,
      @RequestParam(required = false) UUID classroomId,
      @RequestParam(required = false) UUID teacherId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) SessionStatus status,
      Pageable pageable) {
    return new PagedModel<>(
        listSessions
            .list(actor, branchId, classroomId, teacherId, from, to, status, pageable)
            .map(mapper::toResponse));
  }

  @Operation(summary = "Get a session (its teacher and admins of its branch)")
  @GetMapping("/{sessionId}")
  @PreAuthorize(SecurityRoles.ANY_STAFF)
  public SessionResponse get(@CurrentActor Actor actor, @PathVariable UUID sessionId) {
    return mapper.toResponse(getSession.get(actor, sessionId));
  }

  @Operation(summary = "Cancel a planned session (admin of the branch)")
  @PostMapping("/{sessionId}/cancel")
  @PreAuthorize(SecurityRoles.ANY_ADMIN)
  public SessionResponse cancel(@CurrentActor Actor actor, @PathVariable UUID sessionId) {
    return mapper.toResponse(cancelSession.cancel(actor, sessionId));
  }

  @Operation(summary = "Let a substitute teacher take a planned session (admin of the branch)")
  @PutMapping("/{sessionId}/teacher")
  @PreAuthorize(SecurityRoles.ANY_ADMIN)
  public SessionResponse reassign(
      @CurrentActor Actor actor,
      @PathVariable UUID sessionId,
      @Valid @RequestBody ReassignTeacherRequest request) {
    return mapper.toResponse(reassignTeacher.reassign(actor, sessionId, request.teacherId()));
  }
}
