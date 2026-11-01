package com.dershane.attendanceservice.attendance.adapter.in.web;

import com.dershane.attendanceservice.attendance.adapter.in.web.dto.AttendanceRecordResponse;
import com.dershane.attendanceservice.attendance.adapter.in.web.dto.AttendanceSummaryResponse;
import com.dershane.attendanceservice.attendance.application.port.in.AttendanceSummaryQuery;
import com.dershane.attendanceservice.attendance.application.port.in.ListRecordsQuery;
import com.dershane.attendanceservice.shared.adapter.in.security.SecurityRoles;
import com.dershane.attendanceservice.shared.adapter.in.web.CurrentActor;
import com.dershane.attendanceservice.shared.application.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Attendance records")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@PreAuthorize(SecurityRoles.ANY_RECORD_VIEWER)
class RecordController {

  private final ListRecordsQuery listRecords;
  private final AttendanceSummaryQuery attendanceSummary;
  private final AttendanceWebMapper mapper;

  @Operation(
      summary =
          "Attendance records, newest first (student: own; guardian: their child; teacher: own"
              + " sessions; admin: their branch)")
  @GetMapping("/attendance-records")
  public PagedModel<AttendanceRecordResponse> list(
      @CurrentActor Actor actor,
      @RequestParam(required = false) UUID studentId,
      @RequestParam(required = false) UUID classroomId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      Pageable pageable) {
    return new PagedModel<>(
        listRecords
            .list(actor, studentId, classroomId, from, to, pageable)
            .map(mapper::toResponse));
  }

  @Operation(summary = "Counts per status for one student (same visibility as the records)")
  @GetMapping("/attendance-summaries")
  public AttendanceSummaryResponse summary(
      @CurrentActor Actor actor,
      @RequestParam(required = false) UUID studentId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return mapper.toResponse(attendanceSummary.summarize(actor, studentId, from, to));
  }
}
