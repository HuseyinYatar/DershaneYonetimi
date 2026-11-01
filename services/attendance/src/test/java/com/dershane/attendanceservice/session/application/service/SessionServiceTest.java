package com.dershane.attendanceservice.session.application.service;

import static com.dershane.attendanceservice.support.TestJwts.actor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dershane.attendanceservice.session.application.port.in.CreateAdHocSessionCommand;
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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 30);

  @Mock private LoadSessionPort loadSession;
  @Mock private SaveSessionPort saveSession;
  @Mock private SessionQueryPort sessionQuery;

  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final UUID branchId = UUID.randomUUID();
  private final UUID classroomId = UUID.randomUUID();
  private final UUID teacherId = UUID.randomUUID();
  private final UUID subjectId = UUID.randomUUID();
  private final Actor admin = actor(UUID.randomUUID(), branchId, "ADMIN");

  private SessionService service;

  @BeforeEach
  void setUp() {
    BranchAccessPolicy branchAccess = new BranchAccessPolicy();
    service =
        new SessionService(
            loadSession,
            saveSession,
            sessionQuery,
            new SessionLookup(loadSession),
            new SessionAccessPolicy(branchAccess),
            branchAccess,
            new BusinessCalendar(
                Clock.fixed(Instant.parse("2026-10-30T07:00:00Z"), ZoneOffset.UTC),
                "Europe/Istanbul"),
            meters);
  }

  private CreateAdHocSessionCommand adHoc() {
    return new CreateAdHocSessionCommand(
        branchId,
        classroomId,
        teacherId,
        subjectId,
        TODAY.plusDays(1),
        LocalTime.of(14, 0),
        LocalTime.of(14, 40));
  }

  private ClassSession stored(SessionStatus status, LocalDate day, UUID id) {
    return new ClassSession(
        id,
        1L,
        null,
        branchId,
        classroomId,
        teacherId,
        subjectId,
        day,
        LocalTime.of(9, 0),
        LocalTime.of(9, 40),
        status,
        null,
        null);
  }

  private void givenSavedSessionGetsId(UUID id) {
    when(saveSession.save(any()))
        .thenAnswer(
            invocation -> {
              ClassSession s = invocation.getArgument(0);
              return new ClassSession(
                  id,
                  0L,
                  s.getScheduleId(),
                  s.getBranchId(),
                  s.getClassroomId(),
                  s.getTeacherId(),
                  s.getSubjectId(),
                  s.getSessionDate(),
                  s.getStartsAt(),
                  s.getEndsAt(),
                  s.getStatus(),
                  null,
                  null);
            });
  }

  private void givenCreatedSessionIsReadable(UUID id) {
    givenSavedSessionGetsId(id);
    when(sessionQuery.findViewById(id)).thenReturn(Optional.of(view(id)));
  }

  private SessionView view(UUID id) {
    return new SessionView(
        id,
        null,
        branchId,
        classroomId,
        teacherId,
        subjectId,
        TODAY,
        LocalTime.of(9, 0),
        LocalTime.of(9, 40),
        SessionStatus.PLANNED,
        null,
        null,
        Instant.now(),
        Instant.now());
  }

  @Test
  void anAdminOpensAnAdHocSession() {
    UUID id = UUID.randomUUID();
    givenCreatedSessionIsReadable(id);

    SessionView created = service.create(admin, adHoc());

    assertThat(created.id()).isEqualTo(id);
    assertThat(meters.counter(MetricNames.ATTENDANCE_SESSIONS, "status", "planned").count())
        .isEqualTo(1.0);
  }

  @Test
  void anAdHocSessionOfAnotherBranchIsRefused() {
    Actor otherAdmin = actor(UUID.randomUUID(), UUID.randomUUID(), "ADMIN");

    assertThatExceptionOfType(DomainException.class)
        .isThrownBy(() -> service.create(otherAdmin, adHoc()))
        .satisfies(ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BRANCH_SCOPE_VIOLATION));
    verify(saveSession, never()).save(any());
  }

  @Test
  void aSessionCannotOverlapAnotherOfTheSameClassroom() {
    when(loadSession.existsClassroomOverlap(any(), any(), any(), any(), any())).thenReturn(true);

    assertThatExceptionOfType(DomainException.class)
        .isThrownBy(() -> service.create(admin, adHoc()))
        .satisfies(
            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SESSION_CLASSROOM_OVERLAP));
    verify(saveSession, never()).save(any());
  }

  @Test
  void aTeacherCannotRunTwoSessionsAtOnce() {
    when(loadSession.existsTeacherOverlap(any(), any(), any(), any(), any())).thenReturn(true);

    assertThatExceptionOfType(DomainException.class)
        .isThrownBy(() -> service.create(admin, adHoc()))
        .satisfies(ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SESSION_TEACHER_BUSY));
  }

  @Test
  void cancellingIsLimitedToPlannedSessions() {
    UUID id = UUID.randomUUID();
    when(loadSession.findById(id))
        .thenReturn(Optional.of(stored(SessionStatus.COMPLETED, TODAY, id)));

    assertThatExceptionOfType(DomainException.class)
        .isThrownBy(() -> service.cancel(admin, id))
        .satisfies(ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SESSION_NOT_PLANNED));
    verify(saveSession, never()).save(any());
  }

  @Test
  void aSubstituteMustBeFree() {
    UUID id = UUID.randomUUID();
    when(loadSession.findById(id))
        .thenReturn(Optional.of(stored(SessionStatus.PLANNED, TODAY, id)));
    when(loadSession.existsTeacherOverlap(any(), any(), any(), any(), any())).thenReturn(true);

    assertThatExceptionOfType(DomainException.class)
        .isThrownBy(() -> service.reassign(admin, id, UUID.randomUUID()))
        .satisfies(ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SESSION_TEACHER_BUSY));
    verify(saveSession, never()).save(any());
  }

  @Test
  void aTeacherListsOnlyTheirOwnSessions() {
    when(sessionQuery.findViews(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(org.springframework.data.domain.Page.empty());
    Actor teacher = actor(teacherId, branchId, "TEACHER");

    service.list(teacher, null, null, null, null, null, null, PageRequest.of(0, 20));

    verify(sessionQuery).findViews(null, null, teacherId, null, null, null, PageRequest.of(0, 20));
  }

  @Test
  void aTeacherCannotListAnotherTeachersSessions() {
    Actor teacher = actor(teacherId, branchId, "TEACHER");

    assertThatExceptionOfType(DomainException.class)
        .isThrownBy(
            () ->
                service.list(
                    teacher,
                    null,
                    null,
                    UUID.randomUUID(),
                    null,
                    null,
                    null,
                    PageRequest.of(0, 20)))
        .satisfies(ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
  }

  @Test
  void aScheduleDayIsCreatedOnce() {
    UUID scheduleId = UUID.randomUUID();
    when(loadSession.existsByScheduleIdAndSessionDate(scheduleId, TODAY)).thenReturn(true);

    ScheduledSessionOutcome outcome = service.createFromSchedule(scheduled(scheduleId));

    assertThat(outcome).isEqualTo(ScheduledSessionOutcome.ALREADY_EXISTS);
    verify(saveSession, never()).save(any());
  }

  @Test
  void aScheduledDayThatClashesIsSkippedNotFailed() {
    UUID scheduleId = UUID.randomUUID();
    when(loadSession.existsClassroomOverlap(any(), any(), any(), any(), any())).thenReturn(true);

    ScheduledSessionOutcome outcome = service.createFromSchedule(scheduled(scheduleId));

    assertThat(outcome).isEqualTo(ScheduledSessionOutcome.SKIPPED_CONFLICT);
    verify(saveSession, never()).save(any());
  }

  @Test
  void aFreeScheduledDayIsCreated() {
    UUID scheduleId = UUID.randomUUID();
    givenSavedSessionGetsId(UUID.randomUUID());

    ScheduledSessionOutcome outcome = service.createFromSchedule(scheduled(scheduleId));

    assertThat(outcome).isEqualTo(ScheduledSessionOutcome.CREATED);
  }

  @Test
  void elapsedPlannedSessionsExpire() {
    UUID id = UUID.randomUUID();
    ClassSession past = stored(SessionStatus.PLANNED, TODAY.minusDays(1), id);
    when(loadSession.findPlannedBefore(TODAY)).thenReturn(List.of(past));

    int expired = service.expireElapsed();

    assertThat(expired).isEqualTo(1);
    assertThat(past.getStatus()).isEqualTo(SessionStatus.EXPIRED);
    verify(saveSession).save(past);
    assertThat(meters.counter(MetricNames.ATTENDANCE_SESSIONS, "status", "expired").count())
        .isEqualTo(1.0);
  }

  @Test
  void expiringWithNothingToDoSavesNothing() {
    when(loadSession.findPlannedBefore(TODAY)).thenReturn(List.of());

    assertThat(service.expireElapsed()).isZero();
    verify(saveSession, never()).save(any());
  }

  private ScheduledSessionCommand scheduled(UUID scheduleId) {
    return new ScheduledSessionCommand(
        scheduleId,
        branchId,
        classroomId,
        teacherId,
        subjectId,
        TODAY,
        LocalTime.of(9, 0),
        LocalTime.of(9, 40));
  }
}
