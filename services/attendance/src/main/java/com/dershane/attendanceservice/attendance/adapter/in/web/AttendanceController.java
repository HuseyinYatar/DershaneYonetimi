package com.dershane.attendanceservice.attendance.adapter.in.web;

import com.dershane.attendanceservice.attendance.adapter.in.web.dto.AttendanceRecordResponse;
import com.dershane.attendanceservice.attendance.adapter.in.web.dto.AttendanceSubmissionResponse;
import com.dershane.attendanceservice.attendance.adapter.in.web.dto.CorrectRecordRequest;
import com.dershane.attendanceservice.attendance.adapter.in.web.dto.SessionRosterResponse;
import com.dershane.attendanceservice.attendance.adapter.in.web.dto.TakeAttendanceRequest;
import com.dershane.attendanceservice.attendance.application.port.in.CorrectRecordUseCase;
import com.dershane.attendanceservice.attendance.application.port.in.SessionRosterQuery;
import com.dershane.attendanceservice.attendance.application.port.in.TakeAttendanceUseCase;
import com.dershane.attendanceservice.shared.adapter.in.security.SecurityRoles;
import com.dershane.attendanceservice.shared.adapter.in.web.CurrentActor;
import com.dershane.attendanceservice.shared.adapter.in.web.idempotency.Idempotent;
import com.dershane.attendanceservice.shared.application.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Attendance")
@RestController
@RequestMapping("/api/v1/class-sessions/{sessionId}")
@RequiredArgsConstructor
@PreAuthorize(SecurityRoles.ANY_STAFF)
class AttendanceController {

  private final TakeAttendanceUseCase takeAttendance;
  private final CorrectRecordUseCase correctRecord;
  private final SessionRosterQuery sessionRoster;
  private final AttendanceWebMapper mapper;

  @Operation(
      summary =
          "The students of a session with their current status (teacher of the session, admin)")
  @GetMapping("/roster")
  public SessionRosterResponse roster(@CurrentActor Actor actor, @PathVariable UUID sessionId) {
    return mapper.toResponse(sessionRoster.rosterOf(actor, sessionId));
  }

  @Operation(
      summary = "Take attendance with the full list (every student once); only once per session")
  @PostMapping("/attendance")
  @Idempotent
  public ResponseEntity<AttendanceSubmissionResponse> take(
      @CurrentActor Actor actor,
      @PathVariable UUID sessionId,
      @Valid @RequestBody TakeAttendanceRequest request) {
    AttendanceSubmissionResponse result =
        mapper.toResponse(takeAttendance.take(actor, mapper.toCommand(sessionId, request)));
    return ResponseEntity.status(HttpStatus.CREATED).body(result);
  }

  @Operation(
      summary =
          "Correct one student's status after attendance was taken, or add a student who joined"
              + " later (reason required, audited)")
  @PutMapping("/records/{studentId}")
  public AttendanceRecordResponse correct(
      @CurrentActor Actor actor,
      @PathVariable UUID sessionId,
      @PathVariable UUID studentId,
      @Valid @RequestBody CorrectRecordRequest request) {
    return mapper.toResponse(
        correctRecord.correct(actor, mapper.toCommand(sessionId, studentId, request)));
  }
}
