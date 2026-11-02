package com.dershane.attendanceservice.session.application.service;

import com.dershane.attendanceservice.session.application.port.in.CancelSessionUseCase;
import com.dershane.attendanceservice.session.application.port.in.CompleteSessionUseCase;
import com.dershane.attendanceservice.session.application.port.in.CreateAdHocSessionCommand;
import com.dershane.attendanceservice.session.application.port.in.CreateAdHocSessionUseCase;
import com.dershane.attendanceservice.session.application.port.in.CreateScheduledSessionUseCase;
import com.dershane.attendanceservice.session.application.port.in.ExpireSessionsUseCase;
import com.dershane.attendanceservice.session.application.port.in.GetSessionQuery;
import com.dershane.attendanceservice.session.application.port.in.ListSessionsQuery;
import com.dershane.attendanceservice.session.application.port.in.ReassignTeacherUseCase;
import com.dershane.attendanceservice.session.application.port.in.ScheduledSessionCommand;
import com.dershane.attendanceservice.session.application.port.in.ScheduledSessionOutcome;
import com.dershane.attendanceservice.session.application.port.in.SessionView;
import com.dershane.attendanceservice.session.application.port.out.LoadSessionPort;
import com.dershane.attendanceservice.session.application.port.out.SaveSessionPort;
import com.dershane.attendanceservice.session.application.port.out.SessionQueryPort;
import com.dershane.attendanceservice.session.domain.ClassSession;
import com.dershane.attendanceservice.session.domain.SessionStatus;
import com.dershane.attendanceservice.shared.application.Actor;
import com.dershane.attendanceservice.shared.application.BranchAccessPolicy;
import com.dershane.attendanceservice.shared.application.BusinessCalendar;
import com.dershane.attendanceservice.shared.application.MetricNames;
import com.dershane.attendanceservice.shared.domain.DomainException;
import com.dershane.attendanceservice.shared.domain.ErrorCode;
import com.dershane.attendanceservice.shared.domain.Rejections;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.annotation.Observed;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
class SessionService
    implements CreateAdHocSessionUseCase,
        CancelSessionUseCase,
        ReassignTeacherUseCase,
        GetSessionQuery,
        ListSessionsQuery,
        CompleteSessionUseCase,
        CreateScheduledSessionUseCase,
        ExpireSessionsUseCase {

  private final LoadSessionPort loadSession;
  private final SaveSessionPort saveSession;
  private final SessionQueryPort sessionQuery;
  private final SessionLookup sessionLookup;
  private final SessionAccessPolicy sessionAccess;
  private final BranchAccessPolicy branchAccess;
  private final BusinessCalendar calendar;
  private final MeterRegistry meters;

  @Override
  @Transactional
  @Observed(name = "session.create")
  public SessionView create(Actor actor, CreateAdHocSessionCommand command) {
    branchAccess.requireBranchAdmin(actor, command.branchId(), "session.create");
    ClassSession planned =
        ClassSession.plan(
            null,
            command.branchId(),
            command.classroomId(),
            command.teacherId(),
            command.subjectId(),
            command.sessionDate(),
            command.startsAt(),
            command.endsAt());
    requireNoOverlap(planned);
    ClassSession saved = saveSession.save(planned);
    count("planned");
    log.atInfo()
        .addKeyValue("sessionId", saved.getId())
        .addKeyValue("classroomId", saved.getClassroomId())
        .log("Ad-hoc class session created");
    return requireView(saved.getId());
  }

  @Override
  @Transactional
  @Observed(name = "session.cancel")
  public SessionView cancel(Actor actor, UUID sessionId) {
    ClassSession session = sessionLookup.require(sessionId);
    branchAccess.requireBranchAdmin(actor, session.getBranchId(), "session.cancel");
    session.cancel();
    saveSession.save(session);
    count("cancelled");
    log.atInfo().addKeyValue("sessionId", sessionId).log("Class session cancelled");
    return requireView(sessionId);
  }

  @Override
  @Transactional
  @Observed(name = "session.reassign-teacher")
  public SessionView reassign(Actor actor, UUID sessionId, UUID teacherId) {
    ClassSession session = sessionLookup.require(sessionId);
    branchAccess.requireBranchAdmin(actor, session.getBranchId(), "session.reassign-teacher");
    if (session.isTeacher(teacherId)) {
      log.atInfo()
          .addKeyValue("sessionId", sessionId)
          .log("Session already has this teacher; no change");
      return requireView(sessionId);
    }
    session.reassignTeacher(teacherId);
    if (loadSession.existsTeacherOverlap(
        teacherId,
        session.getSessionDate(),
        session.getStartsAt(),
        session.getEndsAt(),
        sessionId)) {
      throw teacherBusy(session);
    }
    saveSession.save(session);
    log.atInfo()
        .addKeyValue("sessionId", sessionId)
        .addKeyValue("teacherId", teacherId)
        .log("Class session teacher reassigned");
    return requireView(sessionId);
  }

  @Override
  @Transactional(readOnly = true)
  public SessionView get(Actor actor, UUID sessionId) {
    sessionAccess.requireStaffOf(actor, sessionLookup.require(sessionId), "session.get");
    return requireView(sessionId);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<SessionView> list(
      Actor actor,
      UUID branchId,
      UUID classroomId,
      UUID teacherId,
      LocalDate from,
      LocalDate to,
      SessionStatus status,
      Pageable pageable) {
    if (actor.isAdmin() || actor.isSuperAdmin()) {
      UUID branchFilter = branchAccess.resolveBranchFilter(actor, branchId, "session.list");
      return sessionQuery.findViews(
          branchFilter, classroomId, teacherId, from, to, status, pageable);
    }
    if (actor.isTeacher()) {
      if (teacherId != null && !teacherId.equals(actor.id())) {
        throw Rejections.reject(
            log,
            ErrorCode.ACCESS_DENIED,
            "A teacher may only list their own sessions",
            "callerId",
            actor.id(),
            "requestedTeacherId",
            teacherId);
      }
      return sessionQuery.findViews(null, classroomId, actor.id(), from, to, status, pageable);
    }
    throw Rejections.reject(
        log,
        ErrorCode.ACCESS_DENIED,
        "Only teachers and admins list sessions",
        "callerId",
        actor.id());
  }

  @Override
  @Transactional
  public void complete(UUID sessionId, UUID takenBy) {
    ClassSession session = sessionLookup.require(sessionId);
    session.complete(takenBy, calendar.now());
    saveSession.save(session);
    count("completed");
  }

  @Override
  @Transactional
  public ScheduledSessionOutcome createFromSchedule(ScheduledSessionCommand command) {
    if (loadSession.existsByScheduleIdAndSessionDate(command.scheduleId(), command.sessionDate())) {
      log.atDebug()
          .addKeyValue("scheduleId", command.scheduleId())
          .addKeyValue("sessionDate", command.sessionDate())
          .log("Session of this schedule and day already exists");
      return ScheduledSessionOutcome.ALREADY_EXISTS;
    }
    ClassSession planned =
        ClassSession.plan(
            command.scheduleId(),
            command.branchId(),
            command.classroomId(),
            command.teacherId(),
            command.subjectId(),
            command.sessionDate(),
            command.startsAt(),
            command.endsAt());
    ErrorCode conflict = findOverlap(planned);
    if (conflict != null) {
      log.atWarn()
          .addKeyValue("errorCode", conflict.name())
          .addKeyValue("scheduleId", command.scheduleId())
          .addKeyValue("sessionDate", command.sessionDate())
          .log("Scheduled session skipped: it overlaps another session");
      return ScheduledSessionOutcome.SKIPPED_CONFLICT;
    }
    ClassSession saved = saveSession.save(planned);
    count("planned");
    log.atInfo()
        .addKeyValue("sessionId", saved.getId())
        .addKeyValue("scheduleId", command.scheduleId())
        .log("Scheduled class session created");
    return ScheduledSessionOutcome.CREATED;
  }

  @Override
  @Transactional
  @Observed(name = "session.expire")
  public int expireElapsed() {
    LocalDate today = calendar.today();
    List<ClassSession> elapsed = loadSession.findPlannedBefore(today);
    if (elapsed.isEmpty()) {
      log.atDebug().addKeyValue("today", today).log("No planned sessions of past days to expire");
      return 0;
    }
    int expired = 0;
    for (ClassSession session : elapsed) {
      if (session.expire(today)) {
        saveSession.save(session);
        count("expired");
        expired++;
      }
    }
    log.atInfo().addKeyValue("expiredCount", expired).log("Elapsed sessions expired");
    return expired;
  }

  private void requireNoOverlap(ClassSession session) {
    ErrorCode conflict = findOverlap(session);
    if (conflict == ErrorCode.SESSION_CLASSROOM_OVERLAP) {
      throw Rejections.reject(
          log,
          conflict,
          "Session rejected: the classroom already has a session at this time",
          "classroomId",
          session.getClassroomId(),
          "sessionDate",
          session.getSessionDate());
    }
    if (conflict != null) {
      throw teacherBusy(session);
    }
  }

  /** The first rule the session breaks, or null when it fits. */
  private ErrorCode findOverlap(ClassSession session) {
    if (loadSession.existsClassroomOverlap(
        session.getClassroomId(),
        session.getSessionDate(),
        session.getStartsAt(),
        session.getEndsAt(),
        session.getId())) {
      return ErrorCode.SESSION_CLASSROOM_OVERLAP;
    }
    if (loadSession.existsTeacherOverlap(
        session.getTeacherId(),
        session.getSessionDate(),
        session.getStartsAt(),
        session.getEndsAt(),
        session.getId())) {
      return ErrorCode.SESSION_TEACHER_BUSY;
    }
    return null;
  }

  private DomainException teacherBusy(ClassSession session) {
    return Rejections.reject(
        log,
        ErrorCode.SESSION_TEACHER_BUSY,
        "Session rejected: the teacher already has a session at this time",
        "teacherId",
        session.getTeacherId(),
        "sessionDate",
        session.getSessionDate());
  }

  private SessionView requireView(UUID sessionId) {
    return sessionQuery
        .findViewById(sessionId)
        .orElseThrow(
            () ->
                Rejections.reject(
                    log,
                    ErrorCode.SESSION_NOT_FOUND,
                    "Class session not found",
                    "sessionId",
                    sessionId));
  }

  private void count(String status) {
    meters.counter(MetricNames.ATTENDANCE_SESSIONS, "status", status).increment();
  }
}
