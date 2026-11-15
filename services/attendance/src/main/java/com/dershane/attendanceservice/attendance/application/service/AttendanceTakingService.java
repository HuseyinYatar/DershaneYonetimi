package com.dershane.attendanceservice.attendance.application.service;

import com.dershane.attendanceservice.attendance.application.AttendanceProperties;
import com.dershane.attendanceservice.attendance.application.port.in.AttendanceSubmissionView;
import com.dershane.attendanceservice.attendance.application.port.in.RosterRowView;
import com.dershane.attendanceservice.attendance.application.port.in.SessionRosterQuery;
import com.dershane.attendanceservice.attendance.application.port.in.SessionRosterView;
import com.dershane.attendanceservice.attendance.application.port.in.TakeAttendanceCommand;
import com.dershane.attendanceservice.attendance.application.port.in.TakeAttendanceUseCase;
import com.dershane.attendanceservice.attendance.application.port.out.LoadRecordPort;
import com.dershane.attendanceservice.attendance.application.port.out.SaveRecordChangePort;
import com.dershane.attendanceservice.attendance.application.port.out.SaveRecordPort;
import com.dershane.attendanceservice.attendance.domain.AttendancePolicy;
import com.dershane.attendanceservice.attendance.domain.AttendanceRecord;
import com.dershane.attendanceservice.attendance.domain.AttendanceRecordChange;
import com.dershane.attendanceservice.attendance.domain.AttendanceStatus;
import com.dershane.attendanceservice.excuse.application.port.in.ExcuseCoverageQuery;
import com.dershane.attendanceservice.roster.application.port.in.ClassroomRosterQuery;
import com.dershane.attendanceservice.roster.application.port.in.RosterEntryView;
import com.dershane.attendanceservice.session.application.port.in.CompleteSessionUseCase;
import com.dershane.attendanceservice.session.application.port.in.LoadSessionQuery;
import com.dershane.attendanceservice.session.application.port.in.SessionAccessGuard;
import com.dershane.attendanceservice.session.domain.ClassSession;
import com.dershane.attendanceservice.session.domain.TakeMode;
import com.dershane.attendanceservice.shared.application.Actor;
import com.dershane.attendanceservice.shared.application.BusinessCalendar;
import com.dershane.attendanceservice.shared.application.MetricNames;
import com.dershane.attendanceservice.shared.domain.DomainException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.annotation.Observed;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
class AttendanceTakingService implements TakeAttendanceUseCase, SessionRosterQuery {

  private static final String TAKE_ACTION = "attendance.take";

  private final LoadSessionQuery sessions;
  private final SessionAccessGuard sessionAccess;
  private final CompleteSessionUseCase completeSession;
  private final ClassroomRosterQuery rosters;
  private final ExcuseCoverageQuery excuses;
  private final LoadRecordPort loadRecords;
  private final SaveRecordPort saveRecords;
  private final SaveRecordChangePort saveChanges;
  private final AttendanceEvents events;
  private final AttendanceProperties properties;
  private final BusinessCalendar calendar;
  private final MeterRegistry meters;

  @Override
  @Transactional
  @Observed(name = "attendance.take")
  public AttendanceSubmissionView take(Actor actor, TakeAttendanceCommand command) {
    try {
      AttendanceSubmissionView result = doTake(actor, command);
      meters.counter(MetricNames.ATTENDANCE_SUBMISSIONS, "result", "taken").increment();
      return result;
    } catch (DomainException ex) {
      meters.counter(MetricNames.ATTENDANCE_SUBMISSIONS, "result", "rejected").increment();
      throw ex;
    }
  }

  @Override
  @Transactional(readOnly = true)
  public SessionRosterView rosterOf(Actor actor, UUID sessionId) {
    ClassSession session = sessions.require(sessionId);
    sessionAccess.requireStaffOf(actor, session, "attendance.roster");
    List<RosterEntryView> roster =
        rosters.rosterOn(session.getClassroomId(), session.getBranchId(), session.getSessionDate());
    Map<UUID, AttendanceRecord> records =
        loadRecords.findBySessionId(sessionId).stream()
            .collect(Collectors.toMap(AttendanceRecord::getStudentId, Function.identity()));
    Map<UUID, UUID> covering =
        excuses.coveredExcuses(
            roster.stream().map(RosterEntryView::studentId).toList(), session.getSessionDate());
    List<RosterRowView> rows =
        roster.stream()
            .map(
                entry -> {
                  AttendanceRecord record = records.get(entry.studentId());
                  UUID excuseId =
                      record != null && record.getExcuseId() != null
                          ? record.getExcuseId()
                          : covering.get(entry.studentId());
                  return new RosterRowView(
                      entry.studentId(),
                      entry.studentNumber(),
                      entry.fullName(),
                      record == null ? null : record.getStatus(),
                      excuseId != null,
                      excuseId);
                })
            .toList();
    return new SessionRosterView(sessionId, session.getStatus(), rows);
  }

  private AttendanceSubmissionView doTake(Actor actor, TakeAttendanceCommand command) {
    ClassSession session = sessions.require(command.sessionId());
    sessionAccess.requireStaffOf(actor, session, TAKE_ACTION);
    boolean admin = sessionAccess.actsAsAdmin(actor, session);
    boolean reasonGiven = command.reason() != null && !command.reason().isBlank();
    TakeMode mode =
        session.checkTakeable(calendar.localNow(), properties.takeEarly(), admin, reasonGiven);

    Set<UUID> rosterIds =
        rosters
            .rosterOn(session.getClassroomId(), session.getBranchId(), session.getSessionDate())
            .stream()
            .map(RosterEntryView::studentId)
            .collect(Collectors.toSet());
    AttendancePolicy.requireExactRoster(
        session.getId(),
        rosterIds,
        command.entries().stream().map(TakeAttendanceCommand.Entry::studentId).toList());

    Map<UUID, UUID> covering = excuses.coveredExcuses(rosterIds, session.getSessionDate());
    Instant now = calendar.now();
    List<AttendanceRecord> records =
        command.entries().stream()
            .map(
                entry -> {
                  AttendancePolicy.Outcome outcome =
                      AttendancePolicy.resolve(entry.status(), covering.get(entry.studentId()));
                  return AttendanceRecord.mark(
                      session.getId(),
                      entry.studentId(),
                      outcome.status(),
                      outcome.excuseId(),
                      entry.note(),
                      now,
                      actor.id());
                })
            .toList();
    List<AttendanceRecord> saved = saveRecords.saveAll(records);

    if (mode == TakeMode.LATE_BY_ADMIN) {
      saveChanges.append(
          saved.stream()
              .map(
                  record ->
                      AttendanceRecordChange.of(
                          record.getId(),
                          null,
                          record.getStatus(),
                          actor.id(),
                          command.reason(),
                          now))
              .toList());
    }
    completeSession.complete(session.getId(), actor.id());
    saved.forEach(record -> events.publishFor(record, session));

    Map<AttendanceStatus, Integer> counts = new EnumMap<>(AttendanceStatus.class);
    saved.forEach(record -> counts.merge(record.getStatus(), 1, Integer::sum));
    log.atInfo()
        .addKeyValue("sessionId", session.getId())
        .addKeyValue("studentCount", saved.size())
        .addKeyValue("mode", mode)
        .log("Attendance taken");
    return new AttendanceSubmissionView(
        session.getId(),
        now,
        counts.getOrDefault(AttendanceStatus.PRESENT, 0),
        counts.getOrDefault(AttendanceStatus.ABSENT, 0),
        counts.getOrDefault(AttendanceStatus.LATE, 0),
        counts.getOrDefault(AttendanceStatus.EXCUSED, 0));
  }
}
