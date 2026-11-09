package com.dershane.attendanceservice.attendance.application.service;

import static com.dershane.attendanceservice.support.TestJwts.actor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dershane.attendanceservice.attendance.application.AttendanceProperties;
import com.dershane.attendanceservice.attendance.application.port.in.AttendanceRecordView;
import com.dershane.attendanceservice.attendance.application.port.in.CorrectRecordCommand;
import com.dershane.attendanceservice.attendance.application.port.out.LoadRecordPort;
import com.dershane.attendanceservice.attendance.application.port.out.RecordQueryPort;
import com.dershane.attendanceservice.attendance.application.port.out.SaveRecordChangePort;
import com.dershane.attendanceservice.attendance.application.port.out.SaveRecordPort;
import com.dershane.attendanceservice.attendance.domain.AttendanceRecord;
import com.dershane.attendanceservice.attendance.domain.AttendanceRecordChange;
import com.dershane.attendanceservice.attendance.domain.AttendanceStatus;
import com.dershane.attendanceservice.excuse.application.port.in.ExcuseCoverageQuery;
import com.dershane.attendanceservice.roster.application.port.in.MembershipQuery;
import com.dershane.attendanceservice.session.application.port.in.LoadSessionQuery;
import com.dershane.attendanceservice.session.application.port.in.SessionAccessGuard;
import com.dershane.attendanceservice.session.domain.ClassSession;
import com.dershane.attendanceservice.session.domain.SessionStatus;
import com.dershane.attendanceservice.shared.application.Actor;
import com.dershane.attendanceservice.shared.application.BusinessCalendar;
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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecordCorrectionServiceTest {

  /** 10:00 in Istanbul on Friday 2026-10-30. */
  private static final Instant NOW = Instant.parse("2026-10-30T07:00:00Z");

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 30);

  @Mock private LoadSessionQuery sessions;
  @Mock private SessionAccessGuard sessionAccess;
  @Mock private MembershipQuery memberships;
  @Mock private ExcuseCoverageQuery excuses;
  @Mock private LoadRecordPort loadRecords;
  @Mock private SaveRecordPort saveRecords;
  @Mock private SaveRecordChangePort saveChanges;
  @Mock private RecordQueryPort recordQuery;
  @Mock private IntegrationEventPublisher publisher;
  @Captor private ArgumentCaptor<List<AttendanceRecordChange>> changesCaptor;

  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final UUID branchId = UUID.randomUUID();
  private final UUID classroomId = UUID.randomUUID();
  private final UUID teacherId = UUID.randomUUID();
  private final UUID studentId = UUID.randomUUID();
  private final UUID sessionId = UUID.randomUUID();
  private final UUID recordId = UUID.randomUUID();
  private final Actor teacher = actor(teacherId, branchId, "TEACHER");

  private RecordCorrectionService service;

  @BeforeEach
  void setUp() {
    BusinessCalendar calendar =
        new BusinessCalendar(Clock.fixed(NOW, ZoneOffset.UTC), "Europe/Istanbul");
    service =
        new RecordCorrectionService(
            sessions,
            sessionAccess,
            memberships,
            excuses,
            loadRecords,
            saveRecords,
            saveChanges,
            recordQuery,
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
        UUID.randomUUID(),
        day,
        LocalTime.of(9, 0),
        LocalTime.of(9, 40),
        status,
        null,
        null);
  }

  private AttendanceRecord record(AttendanceStatus status, UUID excuseId) {
    return new AttendanceRecord(
        recordId, 3L, sessionId, studentId, status, NOW, teacherId, excuseId, null);
  }

  private void givenCompletedSession(LocalDate day) {
    ClassSession session = session(SessionStatus.COMPLETED, day);
    when(sessions.require(sessionId)).thenReturn(session);
  }

  private void givenSavedRecordsKeepTheirIds() {
    when(saveRecords.saveAll(anyList()))
        .thenAnswer(
            invocation ->
                invocation.<List<AttendanceRecord>>getArgument(0).stream()
                    .map(
                        r ->
                            r.getId() != null
                                ? r
                                : new AttendanceRecord(
                                    recordId,
                                    0L,
                                    r.getSessionId(),
                                    r.getStudentId(),
                                    r.getStatus(),
                                    r.getMarkedAt(),
                                    r.getMarkedBy(),
                                    r.getExcuseId(),
                                    r.getNote()))
                    .toList());
    givenViewIsReadable();
  }

  private void givenViewIsReadable() {
    when(recordQuery.findViewById(recordId))
        .thenReturn(
            Optional.of(
                new AttendanceRecordView(
                    recordId,
                    sessionId,
                    studentId,
                    classroomId,
                    UUID.randomUUID(),
                    TODAY,
                    LocalTime.of(9, 0),
                    LocalTime.of(9, 40),
                    AttendanceStatus.PRESENT,
                    null,
                    null,
                    NOW,
                    teacherId)));
  }

  private List<IntegrationEvent> publishedEvents() {
    ArgumentCaptor<IntegrationEvent> captor = ArgumentCaptor.forClass(IntegrationEvent.class);
    verify(publisher, atLeast(0)).publish(captor.capture());
    return captor.getAllValues();
  }

  @Test
  void changingToAbsentAnnouncesTheAbsence() {
    givenCompletedSession(TODAY);
    givenSavedRecordsKeepTheirIds();
    when(excuses.coveredExcuses(anyCollection(), any())).thenReturn(Map.of());
    when(loadRecords.findBySessionIdAndStudentId(sessionId, studentId))
        .thenReturn(Optional.of(record(AttendanceStatus.PRESENT, null)));

    service.correct(
        teacher,
        new CorrectRecordCommand(sessionId, studentId, AttendanceStatus.ABSENT, "Mis-click"));

    List<IntegrationEvent> events = publishedEvents();
    assertThat(events).hasSize(1);
    assertThat(events.getFirst()).isInstanceOf(StudentAbsent.class);
    verify(saveChanges).append(changesCaptor.capture());
    AttendanceRecordChange change = changesCaptor.getValue().getFirst();
    assertThat(change.getOldStatus()).isEqualTo(AttendanceStatus.PRESENT);
    assertThat(change.getNewStatus()).isEqualTo(AttendanceStatus.ABSENT);
    assertThat(change.getChangedBy()).isEqualTo(teacherId);
    assertThat(change.getReason()).isEqualTo("Mis-click");
  }

  @Test
  void changingToATeacherExcuseAnnouncesIt() {
    givenCompletedSession(TODAY);
    givenSavedRecordsKeepTheirIds();
    when(excuses.coveredExcuses(anyCollection(), any())).thenReturn(Map.of());
    when(loadRecords.findBySessionIdAndStudentId(sessionId, studentId))
        .thenReturn(Optional.of(record(AttendanceStatus.ABSENT, null)));

    service.correct(
        teacher,
        new CorrectRecordCommand(
            sessionId, studentId, AttendanceStatus.EXCUSED, "Told me by phone"));

    List<IntegrationEvent> events = publishedEvents();
    assertThat(events).hasSize(1);
    assertThat(events.getFirst()).isInstanceOf(StudentExcusedByTeacher.class);
  }

  @Test
  void changingToPresentAnnouncesNothing() {
    givenCompletedSession(TODAY);
    givenSavedRecordsKeepTheirIds();
    when(excuses.coveredExcuses(anyCollection(), any())).thenReturn(Map.of());
    when(loadRecords.findBySessionIdAndStudentId(sessionId, studentId))
        .thenReturn(Optional.of(record(AttendanceStatus.ABSENT, null)));

    service.correct(
        teacher,
        new CorrectRecordCommand(sessionId, studentId, AttendanceStatus.PRESENT, "Was here"));

    verify(publisher, never()).publish(any());
  }

  @Test
  void anApprovedExcuseKeepsTheCorrectionSilent() {
    UUID excuseId = UUID.randomUUID();
    givenCompletedSession(TODAY);
    givenSavedRecordsKeepTheirIds();
    when(excuses.coveredExcuses(anyCollection(), any())).thenReturn(Map.of(studentId, excuseId));
    when(loadRecords.findBySessionIdAndStudentId(sessionId, studentId))
        .thenReturn(Optional.of(record(AttendanceStatus.PRESENT, null)));

    service.correct(
        teacher,
        new CorrectRecordCommand(sessionId, studentId, AttendanceStatus.ABSENT, "Not here"));

    verify(publisher, never()).publish(any());
    verify(saveChanges).append(changesCaptor.capture());
    assertThat(changesCaptor.getValue().getFirst().getNewStatus())
        .isEqualTo(AttendanceStatus.EXCUSED);
  }

  @Test
  void correctingToTheCurrentOutcomeChangesNothing() {
    givenCompletedSession(TODAY);
    givenViewIsReadable();
    when(excuses.coveredExcuses(anyCollection(), any())).thenReturn(Map.of());
    when(loadRecords.findBySessionIdAndStudentId(sessionId, studentId))
        .thenReturn(Optional.of(record(AttendanceStatus.ABSENT, null)));

    service.correct(
        teacher, new CorrectRecordCommand(sessionId, studentId, AttendanceStatus.ABSENT, "Same"));

    verify(saveRecords, never()).saveAll(anyList());
    verify(saveChanges, never()).append(anyList());
    verify(publisher, never()).publish(any());
  }

  @Test
  void aStudentWhoJoinedLaterGetsARecordWithAnAuditLineWithoutAPreviousStatus() {
    givenCompletedSession(TODAY);
    givenSavedRecordsKeepTheirIds();
    when(excuses.coveredExcuses(anyCollection(), any())).thenReturn(Map.of());
    when(loadRecords.findBySessionIdAndStudentId(sessionId, studentId))
        .thenReturn(Optional.empty());
    when(memberships.wasMemberOn(classroomId, branchId, studentId, TODAY)).thenReturn(true);

    service.correct(
        teacher,
        new CorrectRecordCommand(
            sessionId, studentId, AttendanceStatus.PRESENT, "Joined that day"));

    verify(saveChanges).append(changesCaptor.capture());
    assertThat(changesCaptor.getValue().getFirst().getOldStatus()).isNull();
    assertThat(changesCaptor.getValue().getFirst().getNewStatus())
        .isEqualTo(AttendanceStatus.PRESENT);
  }

  @Test
  void aStudentWhoWasNotInTheClassroomThatDayCannotBeAdded() {
    givenCompletedSession(TODAY);
    when(excuses.coveredExcuses(anyCollection(), any())).thenReturn(Map.of());
    when(loadRecords.findBySessionIdAndStudentId(sessionId, studentId))
        .thenReturn(Optional.empty());
    when(memberships.wasMemberOn(classroomId, branchId, studentId, TODAY)).thenReturn(false);

    assertRejected(
        new CorrectRecordCommand(sessionId, studentId, AttendanceStatus.PRESENT, "Add"),
        ErrorCode.STUDENT_NOT_IN_ROSTER);
  }

  @Test
  void aReasonIsRequired() {
    givenCompletedSession(TODAY);

    assertRejected(
        new CorrectRecordCommand(sessionId, studentId, AttendanceStatus.PRESENT, " "),
        ErrorCode.REASON_REQUIRED);
  }

  @Test
  void aPlannedSessionHasNothingToCorrect() {
    when(sessions.require(sessionId)).thenReturn(session(SessionStatus.PLANNED, TODAY));

    assertRejected(
        new CorrectRecordCommand(sessionId, studentId, AttendanceStatus.PRESENT, "x"),
        ErrorCode.SESSION_NOT_COMPLETED);
  }

  @Test
  void aTeacherCannotCorrectAfterTheWindow() {
    givenCompletedSession(TODAY.minusDays(8));

    assertRejected(
        new CorrectRecordCommand(sessionId, studentId, AttendanceStatus.PRESENT, "Late fix"),
        ErrorCode.CORRECTION_WINDOW_CLOSED);
  }

  @Test
  void anAdminCanCorrectAfterTheWindow() {
    UUID adminId = UUID.randomUUID();
    Actor admin = actor(adminId, branchId, "ADMIN");
    givenCompletedSession(TODAY.minusDays(30));
    givenSavedRecordsKeepTheirIds();
    when(sessionAccess.actsAsAdmin(eq(admin), any(ClassSession.class))).thenReturn(true);
    when(excuses.coveredExcuses(anyCollection(), any())).thenReturn(Map.of());
    when(loadRecords.findBySessionIdAndStudentId(sessionId, studentId))
        .thenReturn(Optional.of(record(AttendanceStatus.ABSENT, null)));

    service.correct(
        admin, new CorrectRecordCommand(sessionId, studentId, AttendanceStatus.PRESENT, "Fix"));

    verify(saveChanges).append(changesCaptor.capture());
    assertThat(changesCaptor.getValue().getFirst().getChangedBy()).isEqualTo(adminId);
  }

  @Test
  void applyingAnExcuseTurnsAbsencesIntoExcusedWithAnAuditLineAndNoEvent() {
    UUID excuseId = UUID.randomUUID();
    UUID admin = UUID.randomUUID();
    when(loadRecords.findAbsencesInCompletedSessions(
            studentId, LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 28)))
        .thenReturn(List.of(record(AttendanceStatus.ABSENT, null)));
    when(saveRecords.saveAll(anyList()))
        .thenAnswer(invocation -> invocation.<List<AttendanceRecord>>getArgument(0));

    int changed =
        service.apply(
            excuseId, studentId, LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 28), admin);

    assertThat(changed).isEqualTo(1);
    verify(saveChanges).append(changesCaptor.capture());
    AttendanceRecordChange change = changesCaptor.getValue().getFirst();
    assertThat(change.getOldStatus()).isEqualTo(AttendanceStatus.ABSENT);
    assertThat(change.getNewStatus()).isEqualTo(AttendanceStatus.EXCUSED);
    assertThat(change.getChangedBy()).isEqualTo(admin);
    verify(publisher, never()).publish(any());
  }

  @Test
  void applyingAnExcuseWithNoAbsencesDoesNothing() {
    when(loadRecords.findAbsencesInCompletedSessions(eq(studentId), any(), any()))
        .thenReturn(List.of());

    int changed = service.apply(UUID.randomUUID(), studentId, TODAY, TODAY, UUID.randomUUID());

    assertThat(changed).isZero();
    verify(saveRecords, never()).saveAll(anyList());
  }

  private void assertRejected(CorrectRecordCommand command, ErrorCode expected) {
    assertThatExceptionOfType(DomainException.class)
        .isThrownBy(() -> service.correct(teacher, command))
        .satisfies(ex -> assertThat(ex.getErrorCode()).isEqualTo(expected));
    verify(saveRecords, never()).saveAll(anyList());
  }
}
