package com.dershane.attendanceservice.attendance.application.service;

import com.dershane.attendanceservice.attendance.application.port.in.AttendanceRecordView;
import com.dershane.attendanceservice.attendance.application.port.in.AttendanceSummaryQuery;
import com.dershane.attendanceservice.attendance.application.port.in.AttendanceSummaryView;
import com.dershane.attendanceservice.attendance.application.port.in.ListRecordsQuery;
import com.dershane.attendanceservice.attendance.application.port.out.RecordFilter;
import com.dershane.attendanceservice.attendance.application.port.out.RecordQueryPort;
import com.dershane.attendanceservice.attendance.domain.AttendanceStatus;
import com.dershane.attendanceservice.roster.application.port.in.GuardianAccessQuery;
import com.dershane.attendanceservice.shared.application.Actor;
import com.dershane.attendanceservice.shared.application.BranchAccessPolicy;
import com.dershane.attendanceservice.shared.domain.ErrorCode;
import com.dershane.attendanceservice.shared.domain.Rejections;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Who may see which attendance records lives here once, for lists and summaries alike. */
@Slf4j
@Service
@RequiredArgsConstructor
class RecordQueryService implements ListRecordsQuery, AttendanceSummaryQuery {

  private final RecordQueryPort recordQuery;
  private final GuardianAccessQuery guardians;
  private final BranchAccessPolicy branchAccess;

  @Override
  @Transactional(readOnly = true)
  public Page<AttendanceRecordView> list(
      Actor actor,
      UUID studentId,
      UUID classroomId,
      LocalDate from,
      LocalDate to,
      Pageable pageable) {
    return recordQuery.findViews(scopeFor(actor, studentId, classroomId, from, to), pageable);
  }

  @Override
  @Transactional(readOnly = true)
  public AttendanceSummaryView summarize(
      Actor actor, UUID studentId, LocalDate from, LocalDate to) {
    RecordFilter filter = scopeFor(actor, studentId, null, from, to);
    if (filter.studentId() == null) {
      throw Rejections.reject(
          log,
          ErrorCode.STUDENT_FILTER_REQUIRED,
          "Summary rejected: no student given",
          "callerId",
          actor.id());
    }
    Map<AttendanceStatus, Long> counts = recordQuery.countByStatus(filter);
    long present = counts.getOrDefault(AttendanceStatus.PRESENT, 0L);
    long absent = counts.getOrDefault(AttendanceStatus.ABSENT, 0L);
    long late = counts.getOrDefault(AttendanceStatus.LATE, 0L);
    long excused = counts.getOrDefault(AttendanceStatus.EXCUSED, 0L);
    return new AttendanceSummaryView(
        filter.studentId(),
        from,
        to,
        present,
        absent,
        late,
        excused,
        present + absent + late + excused);
  }

  /** Narrows the request to what the caller may see. */
  private RecordFilter scopeFor(
      Actor actor, UUID studentId, UUID classroomId, LocalDate from, LocalDate to) {
    if (actor.isAdmin() || actor.isSuperAdmin()) {
      UUID branchId = branchAccess.resolveBranchFilter(actor, null, "attendance.records");
      return new RecordFilter(branchId, null, studentId, classroomId, from, to);
    }
    if (actor.isTeacher()) {
      return new RecordFilter(null, actor.id(), studentId, classroomId, from, to);
    }
    if (actor.isGuardian()) {
      if (studentId == null) {
        throw Rejections.reject(
            log,
            ErrorCode.STUDENT_FILTER_REQUIRED,
            "A guardian must name the student",
            "callerId",
            actor.id());
      }
      if (!guardians.isGuardianOf(actor.id(), studentId)) {
        throw Rejections.reject(
            log,
            ErrorCode.GUARDIAN_NOT_LINKED,
            "Records requested for a student who is not linked to the caller",
            "callerId",
            actor.id(),
            "studentId",
            studentId);
      }
      return new RecordFilter(null, null, studentId, classroomId, from, to);
    }
    if (actor.isStudent()) {
      if (studentId != null && !studentId.equals(actor.id())) {
        throw Rejections.reject(
            log,
            ErrorCode.ACCESS_DENIED,
            "A student may only see their own records",
            "callerId",
            actor.id(),
            "requestedStudentId",
            studentId);
      }
      return new RecordFilter(null, null, actor.id(), classroomId, from, to);
    }
    throw Rejections.reject(
        log,
        ErrorCode.ACCESS_DENIED,
        "Caller has no role that may see attendance records",
        "callerId",
        actor.id());
  }
}
