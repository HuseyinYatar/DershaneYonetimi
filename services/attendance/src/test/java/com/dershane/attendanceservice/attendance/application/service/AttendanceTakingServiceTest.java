package com.dershane.attendanceservice.attendance.application.service;

import static com.dershane.attendanceservice.support.TestJwts.actor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dershane.attendanceservice.attendance.application.AttendanceProperties;
import com.dershane.attendanceservice.attendance.application.port.in.AttendanceSubmissionView;
import com.dershane.attendanceservice.attendance.application.port.in.RosterRowView;
import com.dershane.attendanceservice.attendance.application.port.in.SessionRosterView;
import com.dershane.attendanceservice.attendance.application.port.in.TakeAttendanceCommand;
import com.dershane.attendanceservice.attendance.application.port.in.TakeAttendanceCommand.Entry;
import com.dershane.attendanceservice.attendance.application.port.out.LoadRecordPort;
import com.dershane.attendanceservice.attendance.application.port.out.SaveRecordChangePort;
import com.dershane.attendanceservice.attendance.application.port.out.SaveRecordPort;
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
import com.dershane.attendanceservice.session.domain.SessionStatus;
import com.dershane.attendanceservice.shared.application.Actor;
import com.dershane.attendanceservice.shared.application.BusinessCalendar;
import com.dershane.attendanceservice.shared.application.MetricNames;
import com.dershane.attendanceservice.shared.application.event.IntegrationEvent;
import com.dershane.attendanceservice.shared.application.event.IntegrationEvent.StudentAbsent;
import com.dershane.attendanceservice.shared.application.event.IntegrationEvent.StudentExcusedByTeacher;
import com.dershane.attendanceservice.shared.application.port.out.IntegrationEventPublisher;
import com.dershane.attendanceservice.shared.domain.DomainException;
import com.dershane.attendanceservice.shared.domain.ErrorCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AttendanceTakingServiceTest {

  /** 10:00 in Istanbul, a Friday. */
  private static final Instant NOW = Instant.parse("2026-10-30T07:00:00Z");

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 30);

  @Mock private LoadSessionQuery sessions;
  @Mock private SessionAccessGuard sessionAccess;
  @Mock private CompleteSessionUseCase completeSession;
  @Mock private ClassroomRosterQuery rosters;
  @Mock private ExcuseCoverageQuery excuses;
  @Mock private LoadRecordPort loadRecords;
  @Mock private SaveRecordPort saveRecords;
  @Mock private SaveRecordChangePort saveChanges;
  @Mock private IntegrationEventPublisher publisher;
  @Captor private ArgumentCaptor<List<AttendanceRecord>> recordsCaptor;
  @Captor private ArgumentCaptor<List<AttendanceRecordChange>> changesCaptor;

  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final UUID branchId = UUID.randomUUID();
  private final UUID classroomId = UUID.randomUUID();
  private final UUID teacherId = UUID.randomUUID();
  private final UUID subjectId = UUID.randomUUID();
  private final UUID ali = UUID.randomUUID();
  private final UUID ayse = UUID.randomUUID();
  private final UUID can = UUID.randomUUID();
  private final UUID sessionId = UUID.randomUUID();
  private final Actor teacher = actor(teacherId, branchId, "TEACHER");

  private AttendanceTakingService service;

  @BeforeEach
  void setUp() {
    BusinessCalendar calendar =
        new BusinessCalendar(Clock.fixed(NOW, ZoneOffset.UTC), "Europe/Istanbul");
    service =
        new AttendanceTakingService(
            sessions,
            sessionAccess,
            completeSession,
            rosters,
            excuses,
            loadRecords,
            saveRecords,
            saveChanges,
            new AttendanceEvents(publisher, meters),
            new AttendanceProperties(new AttendanceProperties.Session(15), 7),
            calendar,
            meters);
  }

  private ClassSession session(SessionStatus status, LocalDate day) {
    return new ClassSession(
        sessionId,
        0L,
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

  private void givenSession(ClassSession session, List<UUID> roster) {
    when(sessions.require(sessionId)).thenReturn(session);
    when(rosters.rosterOn(classroomId, session.getSessionDate()))
        .thenReturn(
            roster.stream().map(id -> new RosterEntryView(id, "no-" + id, "name-" + id)).toList());
  }

  private void givenSavedRecordsGetIds() {
    when(saveRecords.saveAll(anyList()))
        .thenAnswer(
            invocation ->
                invocation.<List<AttendanceRecord>>getArgument(0).stream()
                    .map(
                        r ->
                            new AttendanceRecord(
                                UUID.randomUUID(),
                                0L,
                                r.getSessionId(),
                                r.getStudentId(),
                                r.getStatus(),
                                r.getMarkedAt(),
                                r.getMarkedBy(),
                                r.getExcuseId(),
                                r.getNote()))
                    .toList());
  }

  private static TakeAttendanceCommand command(UUID sessionId, String reason, Entry... entries) {
    return new TakeAttendanceCommand(sessionId, List.of(entries), reason);
  }

  private List<IntegrationEvent> publishedEvents() {
    ArgumentCaptor<IntegrationEvent> captor = ArgumentCaptor.forClass(IntegrationEvent.class);
    verify(publisher, atLeast(0)).publish(captor.capture());
    return captor.getAllValues();
  }

  @Test
  void absentStudentsAndTeacherExcusesAreAnnouncedAndNothingElse() {
    givenSession(session(SessionStatus.PLANNED, TODAY), List.of(ali, ayse, can));
    givenSavedRecordsGetIds();
    when(excuses.coveredExcuses(anyCollection(), eq(TODAY))).thenReturn(Map.of());

    AttendanceSubmissionView result =
        service.take(
            teacher,
            command(
                sessionId,
                null,
                new Entry(ali, AttendanceStatus.PRESENT, null),
                new Entry(ayse, AttendanceStatus.ABSENT, null),
                new Entry(can, AttendanceStatus.EXCUSED, "Left early with permission")));

    assertThat(result.present()).isEqualTo(1);
    assertThat(result.absent()).isEqualTo(1);
    assertThat(result.excused()).isEqualTo(1);
    assertThat(result.late()).isZero();
    verify(completeSession).complete(sessionId, teacherId);
    List<IntegrationEvent> events = publishedEvents();
    assertThat(events).hasSize(2);
    assertThat(events.get(0)).isInstanceOf(StudentAbsent.class);
    StudentAbsent absent = (StudentAbsent) events.get(0);
    assertThat(absent.studentId()).isEqualTo(ayse);
    assertThat(absent.sessionId()).isEqualTo(sessionId);
    assertThat(absent.classroomId()).isEqualTo(classroomId);
    assertThat(absent.branchId()).isEqualTo(branchId);
    assertThat(absent.subjectId()).isEqualTo(subjectId);
    assertThat(absent.sessionDate()).isEqualTo(TODAY);
    assertThat(absent.startTime()).isEqualTo(LocalTime.of(9, 0));
    assertThat(absent.recordId()).isNotNull();
    assertThat(events.get(1)).isInstanceOf(StudentExcusedByTeacher.class);
    assertThat(((StudentExcusedByTeacher) events.get(1)).studentId()).isEqualTo(can);
    assertThat(meters.counter(MetricNames.ATTENDANCE_SUBMISSIONS, "result", "taken").count())
        .isEqualTo(1.0);
    assertThat(meters.counter(MetricNames.ABSENCES_PUBLISHED).count()).isEqualTo(1.0);
    assertThat(meters.counter(MetricNames.EXCUSED_PUBLISHED).count()).isEqualTo(1.0);
  }

  @Test
  void lateStudentsAreNotAnnounced() {
    givenSession(session(SessionStatus.PLANNED, TODAY), List.of(ali));
    givenSavedRecordsGetIds();
    when(excuses.coveredExcuses(anyCollection(), any())).thenReturn(Map.of());

    service.take(teacher, command(sessionId, null, new Entry(ali, AttendanceStatus.LATE, null)));

    verify(publisher, never()).publish(any());
  }

  @Test
  void anApprovedExcuseMakesMissingStudentsExcusedWithoutAnyEvent() {
    UUID excuseId = UUID.randomUUID();
    givenSession(session(SessionStatus.PLANNED, TODAY), List.of(ali, ayse, can));
    givenSavedRecordsGetIds();
    when(excuses.coveredExcuses(anyCollection(), eq(TODAY)))
        .thenReturn(Map.of(ali, excuseId, ayse, excuseId, can, excuseId));

    service.take(
        teacher,
        command(
            sessionId,
            null,
            new Entry(ali, AttendanceStatus.ABSENT, null),
            new Entry(ayse, AttendanceStatus.EXCUSED, null),
            new Entry(can, AttendanceStatus.PRESENT, null)));

    verify(saveRecords).saveAll(recordsCaptor.capture());
    assertThat(recordsCaptor.getValue())
        .extracting(AttendanceRecord::getStatus, AttendanceRecord::getExcuseId)
        .containsExactly(
            tuple(AttendanceStatus.EXCUSED, excuseId),
            tuple(AttendanceStatus.EXCUSED, excuseId),
            tuple(AttendanceStatus.PRESENT, null));
    verify(publisher, never()).publish(any());
  }

  @Test
  void aForgottenStudentRejectsTheWholeSubmissionAndSavesNothing() {
    givenSession(session(SessionStatus.PLANNED, TODAY), List.of(ali, ayse));

    assertThatExceptionOfType(DomainException.class)
        .isThrownBy(
            () ->
                service.take(
                    teacher,
                    command(sessionId, null, new Entry(ali, AttendanceStatus.PRESENT, null))))
        .satisfies(ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ROSTER_MISMATCH));

    verify(saveRecords, never()).saveAll(anyList());
    verify(completeSession, never()).complete(any(), any());
    assertThat(meters.counter(MetricNames.ATTENDANCE_SUBMISSIONS, "result", "rejected").count())
        .isEqualTo(1.0);
  }

  @Test
  void aStudentOutsideTheRosterIsRejected() {
    givenSession(session(SessionStatus.PLANNED, TODAY), List.of(ali));

    assertThatExceptionOfType(DomainException.class)
        .isThrownBy(
            () ->
                service.take(
                    teacher,
                    command(
                        sessionId,
                        null,
                        new Entry(ali, AttendanceStatus.PRESENT, null),
                        new Entry(UUID.randomUUID(), AttendanceStatus.PRESENT, null))))
        .satisfies(ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ROSTER_MISMATCH));
  }

  @Test
  void aTeacherCannotTakeAnExpiredSession() {
    when(sessions.require(sessionId))
        .thenReturn(session(SessionStatus.EXPIRED, TODAY.minusDays(1)));

    assertThatExceptionOfType(DomainException.class)
        .isThrownBy(
            () ->
                service.take(
                    teacher,
                    command(sessionId, "late", new Entry(ali, AttendanceStatus.PRESENT, null))))
        .satisfies(ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SESSION_EXPIRED));

    verify(saveRecords, never()).saveAll(anyList());
  }

  @Test
  void anAdminTakingAnExpiredSessionLeavesAnAuditLinePerStudent() {
    UUID adminId = UUID.randomUUID();
    Actor admin = actor(adminId, branchId, "ADMIN");
    ClassSession expired = session(SessionStatus.EXPIRED, TODAY.minusDays(1));
    when(sessions.require(sessionId)).thenReturn(expired);
    when(sessionAccess.actsAsAdmin(admin, expired)).thenReturn(true);
    when(rosters.rosterOn(classroomId, expired.getSessionDate()))
        .thenReturn(List.of(new RosterEntryView(ali, "1", "Ali")));
    givenSavedRecordsGetIds();
    when(excuses.coveredExcuses(anyCollection(), any())).thenReturn(Map.of());

    service.take(
        admin,
        command(sessionId, "Teacher was ill", new Entry(ali, AttendanceStatus.ABSENT, null)));

    verify(saveChanges).append(changesCaptor.capture());
    assertThat(changesCaptor.getValue()).hasSize(1);
    AttendanceRecordChange change = changesCaptor.getValue().getFirst();
    assertThat(change.getOldStatus()).isNull();
    assertThat(change.getNewStatus()).isEqualTo(AttendanceStatus.ABSENT);
    assertThat(change.getChangedBy()).isEqualTo(adminId);
    assertThat(change.getReason()).isEqualTo("Teacher was ill");
    verify(completeSession).complete(sessionId, adminId);
  }

  @Test
  void aNormalSubmissionWritesNoAuditLines() {
    givenSession(session(SessionStatus.PLANNED, TODAY), List.of(ali));
    givenSavedRecordsGetIds();
    when(excuses.coveredExcuses(anyCollection(), any())).thenReturn(Map.of());

    service.take(teacher, command(sessionId, null, new Entry(ali, AttendanceStatus.PRESENT, null)));

    verify(saveChanges, never()).append(anyList());
  }

  @Test
  void aSecondSubmissionIsRejected() {
    when(sessions.require(sessionId)).thenReturn(session(SessionStatus.COMPLETED, TODAY));

    assertThatExceptionOfType(DomainException.class)
        .isThrownBy(
            () ->
                service.take(
                    teacher,
                    command(sessionId, null, new Entry(ali, AttendanceStatus.PRESENT, null))))
        .satisfies(
            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ATTENDANCE_ALREADY_TAKEN));
  }

  @Test
  void aSubmissionForADifferentTeacherIsRefusedByTheAccessGuard() {
    ClassSession session = session(SessionStatus.PLANNED, TODAY);
    when(sessions.require(sessionId)).thenReturn(session);
    doThrow(new DomainException(ErrorCode.NOT_SESSION_TEACHER))
        .when(sessionAccess)
        .requireStaffOf(any(), eq(session), any());

    assertThatExceptionOfType(DomainException.class)
        .isThrownBy(
            () ->
                service.take(
                    actor(UUID.randomUUID(), branchId, "TEACHER"),
                    command(sessionId, null, new Entry(ali, AttendanceStatus.PRESENT, null))))
        .satisfies(ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOT_SESSION_TEACHER));

    verify(saveRecords, never()).saveAll(anyList());
  }

  @Test
  void theRosterShowsApprovedExcusesBeforeAndAfterAttendanceIsTaken() {
    UUID excuseId = UUID.randomUUID();
    ClassSession planned = session(SessionStatus.PLANNED, TODAY);
    when(sessions.require(sessionId)).thenReturn(planned);
    when(rosters.rosterOn(classroomId, TODAY))
        .thenReturn(
            List.of(new RosterEntryView(ali, "1", "Ali"), new RosterEntryView(ayse, "2", "Ayse")));
    when(loadRecords.findBySessionId(sessionId))
        .thenReturn(
            List.of(
                new AttendanceRecord(
                    UUID.randomUUID(),
                    0L,
                    sessionId,
                    ayse,
                    AttendanceStatus.PRESENT,
                    NOW,
                    teacherId,
                    null,
                    null)));
    when(excuses.coveredExcuses(anyCollection(), eq(TODAY))).thenReturn(Map.of(ali, excuseId));

    SessionRosterView roster = service.rosterOf(teacher, sessionId);

    assertThat(roster.students())
        .extracting(
            RosterRowView::studentId,
            RosterRowView::status,
            RosterRowView::excused,
            RosterRowView::excuseId)
        .containsExactly(
            tuple(ali, null, true, excuseId), tuple(ayse, AttendanceStatus.PRESENT, false, null));
    verify(sessionAccess).requireStaffOf(teacher, planned, "attendance.roster");
    verify(publisher, times(0)).publish(any());
  }
}
