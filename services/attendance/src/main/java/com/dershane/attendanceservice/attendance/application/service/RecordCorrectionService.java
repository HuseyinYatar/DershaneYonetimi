package com.dershane.attendanceservice.attendance.application.service;

import com.dershane.attendanceservice.attendance.application.AttendanceProperties;
import com.dershane.attendanceservice.attendance.application.port.in.ApplyExcuseUseCase;
import com.dershane.attendanceservice.attendance.application.port.in.AttendanceRecordView;
import com.dershane.attendanceservice.attendance.application.port.in.CorrectRecordCommand;
import com.dershane.attendanceservice.attendance.application.port.in.CorrectRecordUseCase;
import com.dershane.attendanceservice.attendance.application.port.out.LoadRecordPort;
import com.dershane.attendanceservice.attendance.application.port.out.RecordQueryPort;
import com.dershane.attendanceservice.attendance.application.port.out.SaveRecordChangePort;
import com.dershane.attendanceservice.attendance.application.port.out.SaveRecordPort;
import com.dershane.attendanceservice.attendance.domain.AttendancePolicy;
import com.dershane.attendanceservice.attendance.domain.AttendanceRecord;
import com.dershane.attendanceservice.attendance.domain.AttendanceRecordChange;
import com.dershane.attendanceservice.attendance.domain.AttendanceStatus;
import com.dershane.attendanceservice.excuse.application.port.in.ExcuseCoverageQuery;
import com.dershane.attendanceservice.roster.application.port.in.MembershipQuery;
import com.dershane.attendanceservice.session.application.port.in.LoadSessionQuery;
import com.dershane.attendanceservice.session.application.port.in.SessionAccessGuard;
import com.dershane.attendanceservice.session.domain.ClassSession;
import com.dershane.attendanceservice.shared.application.Actor;
import com.dershane.attendanceservice.shared.application.BusinessCalendar;
import com.dershane.attendanceservice.shared.application.MetricNames;
import com.dershane.attendanceservice.shared.domain.ErrorCode;
import com.dershane.attendanceservice.shared.domain.Rejections;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.annotation.Observed;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
class RecordCorrectionService implements CorrectRecordUseCase, ApplyExcuseUseCase {

  private static final String CORRECT_ACTION = "attendance.correct";

  /** Audit text for changes made by an excuse approval (system text, not user input). */
  private static final String EXCUSE_APPROVED_REASON = "Excuse approved";

  private final LoadSessionQuery sessions;
  private final SessionAccessGuard sessionAccess;
  private final MembershipQuery memberships;
  private final ExcuseCoverageQuery excuses;
  private final LoadRecordPort loadRecords;
  private final SaveRecordPort saveRecords;
  private final SaveRecordChangePort saveChanges;
  private final RecordQueryPort recordQuery;
  private final AttendanceEvents events;
  private final AttendanceProperties properties;
  private final BusinessCalendar calendar;
  private final MeterRegistry meters;

  @Override
  @Transactional
  @Observed(name = "attendance.correct")
  public AttendanceRecordView correct(Actor actor, CorrectRecordCommand command) {
    ClassSession session = sessions.require(command.sessionId());
    sessionAccess.requireStaffOf(actor, session, CORRECT_ACTION);
    session.requireCompleted(CORRECT_ACTION);
    requireReason(command, session);
    if (!sessionAccess.actsAsAdmin(actor, session)) {
      AttendancePolicy.requireWithinCorrectionWindow(
          session.getId(),
          session.getSessionDate(),
          calendar.today(),
          properties.correctionWindowDays());
    }

    UUID coveringExcuse =
        excuses
            .coveredExcuses(List.of(command.studentId()), session.getSessionDate())
            .get(command.studentId());
    AttendancePolicy.Outcome outcome = AttendancePolicy.resolve(command.status(), coveringExcuse);
    Instant now = calendar.now();

    Optional<AttendanceRecord> existing =
        loadRecords.findBySessionIdAndStudentId(session.getId(), command.studentId());
    AttendanceStatus oldStatus;
    AttendanceRecord record;
    if (existing.isPresent()) {
      record = existing.get();
      oldStatus = record.getStatus();
      if (!record.changeTo(outcome.status(), outcome.excuseId(), now, actor.id())) {
        log.atInfo()
            .addKeyValue("recordId", record.getId())
            .addKeyValue("status", oldStatus)
            .log("Correction changes nothing; record already has this outcome");
        return requireView(record.getId());
      }
    } else {
      requireWasMember(session, command.studentId());
      oldStatus = null;
      record =
          AttendanceRecord.mark(
              session.getId(),
              command.studentId(),
              outcome.status(),
              outcome.excuseId(),
              null,
              now,
              actor.id());
    }
    AttendanceRecord saved = saveRecords.saveAll(List.of(record)).getFirst();
    saveChanges.append(
        List.of(
            AttendanceRecordChange.of(
                saved.getId(), oldStatus, saved.getStatus(), actor.id(), command.reason(), now)));
    events.publishFor(saved, session);
    meters.counter(MetricNames.ATTENDANCE_CORRECTIONS).increment();
    log.atInfo()
        .addKeyValue("recordId", saved.getId())
        .addKeyValue("sessionId", session.getId())
        .addKeyValue("oldStatus", oldStatus)
        .addKeyValue("newStatus", saved.getStatus())
        .log("Attendance record corrected");
    return requireView(saved.getId());
  }

  @Override
  @Transactional
  @Observed(name = "attendance.apply-excuse")
  public int apply(
      UUID excuseId, UUID studentId, LocalDate fromDate, LocalDate toDate, UUID decidedBy) {
    List<AttendanceRecord> absences =
        loadRecords.findAbsencesInCompletedSessions(studentId, fromDate, toDate);
    if (absences.isEmpty()) {
      log.atDebug()
          .addKeyValue("excuseId", excuseId)
          .addKeyValue("studentId", studentId)
          .log("Approved excuse covers no recorded absences");
      return 0;
    }
    Instant now = calendar.now();
    absences.forEach(record -> record.changeTo(AttendanceStatus.EXCUSED, excuseId, now, decidedBy));
    List<AttendanceRecord> saved = saveRecords.saveAll(absences);
    saveChanges.append(
        saved.stream()
            .map(
                record ->
                    AttendanceRecordChange.of(
                        record.getId(),
                        AttendanceStatus.ABSENT,
                        AttendanceStatus.EXCUSED,
                        decidedBy,
                        EXCUSE_APPROVED_REASON,
                        now))
            .toList());
    meters.counter(MetricNames.ATTENDANCE_CORRECTIONS).increment(saved.size());
    log.atInfo()
        .addKeyValue("excuseId", excuseId)
        .addKeyValue("studentId", studentId)
        .addKeyValue("recordCount", saved.size())
        .log("Absences excused by an approved excuse");
    return saved.size();
  }

  private void requireReason(CorrectRecordCommand command, ClassSession session) {
    if (command.reason() == null || command.reason().isBlank()) {
      throw Rejections.reject(
          log,
          ErrorCode.REASON_REQUIRED,
          "Correction rejected: no reason given",
          "sessionId",
          session.getId(),
          "studentId",
          command.studentId());
    }
  }

  private void requireWasMember(ClassSession session, UUID studentId) {
    if (!memberships.wasMemberOn(
        session.getClassroomId(), session.getBranchId(), studentId, session.getSessionDate())) {
      throw Rejections.reject(
          log,
          ErrorCode.STUDENT_NOT_IN_ROSTER,
          "Correction rejected: the student was not in the classroom on the session date",
          "sessionId",
          session.getId(),
          "studentId",
          studentId);
    }
  }

  private AttendanceRecordView requireView(UUID recordId) {
    return recordQuery
        .findViewById(recordId)
        .orElseThrow(
            () ->
                Rejections.reject(
                    log,
                    ErrorCode.RESOURCE_NOT_FOUND,
                    "Attendance record not found",
                    "recordId",
                    recordId));
  }
}
