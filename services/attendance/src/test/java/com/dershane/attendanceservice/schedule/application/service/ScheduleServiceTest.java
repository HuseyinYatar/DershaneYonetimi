package com.dershane.attendanceservice.schedule.application.service;

import static com.dershane.attendanceservice.support.TestJwts.actor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dershane.attendanceservice.schedule.application.port.in.GenerateSessionsResult;
import com.dershane.attendanceservice.schedule.application.port.in.ScheduleView;
import com.dershane.attendanceservice.schedule.application.port.in.UpdateScheduleCommand;
import com.dershane.attendanceservice.schedule.application.port.out.LoadSchedulePort;
import com.dershane.attendanceservice.schedule.application.port.out.SaveSchedulePort;
import com.dershane.attendanceservice.schedule.application.port.out.ScheduleQueryPort;
import com.dershane.attendanceservice.schedule.domain.ClassSchedule;
import com.dershane.attendanceservice.session.application.port.in.CreateScheduledSessionUseCase;
import com.dershane.attendanceservice.session.application.port.in.ScheduledSessionCommand;
import com.dershane.attendanceservice.session.application.port.in.ScheduledSessionOutcome;
import com.dershane.attendanceservice.shared.application.Actor;
import com.dershane.attendanceservice.shared.application.BranchAccessPolicy;
import com.dershane.attendanceservice.shared.application.BusinessCalendar;
import com.dershane.attendanceservice.shared.domain.DomainException;
import com.dershane.attendanceservice.shared.domain.ErrorCode;
import java.time.Clock;
import java.time.DayOfWeek;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

  /** Friday 2026-10-30, 10:00 in Istanbul. */
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 30);

  @Mock private LoadSchedulePort loadSchedule;
  @Mock private SaveSchedulePort saveSchedule;
  @Mock private ScheduleQueryPort scheduleQuery;
  @Mock private CreateScheduledSessionUseCase createSession;

  private final UUID branchId = UUID.randomUUID();
  private final Actor admin = actor(UUID.randomUUID(), branchId, "ADMIN");

  private ScheduleService service;

  @BeforeEach
  void setUp() {
    service =
        new ScheduleService(
            loadSchedule,
            saveSchedule,
            scheduleQuery,
            createSession,
            new BranchAccessPolicy(),
            new BusinessCalendar(
                Clock.fixed(Instant.parse("2026-10-30T07:00:00Z"), ZoneOffset.UTC),
                "Europe/Istanbul"));
  }

  private ClassSchedule schedule(DayOfWeek day, LocalDate validFrom, LocalDate validTo) {
    return new ClassSchedule(
        UUID.randomUUID(),
        0L,
        branchId,
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        day,
        LocalTime.of(9, 0),
        LocalTime.of(9, 40),
        validFrom,
        validTo,
        true);
  }

  @Test
  void generatesASessionForEveryMatchingDayInTheHorizon() {
    // Fridays 30 Oct and 6 Nov fall inside the next 7 days (30 Oct .. 6 Nov).
    ClassSchedule fridays = schedule(DayOfWeek.FRIDAY, TODAY.minusMonths(1), null);
    when(loadSchedule.findActiveValidBetween(TODAY, TODAY.plusDays(7)))
        .thenReturn(List.of(fridays));
    when(createSession.createFromSchedule(any())).thenReturn(ScheduledSessionOutcome.CREATED);

    GenerateSessionsResult result = service.generate(7);

    assertThat(result).isEqualTo(new GenerateSessionsResult(2, 0, 0, 0));
    ArgumentCaptor<ScheduledSessionCommand> commands =
        ArgumentCaptor.forClass(ScheduledSessionCommand.class);
    verify(createSession, times(2)).createFromSchedule(commands.capture());
    assertThat(commands.getAllValues())
        .extracting(ScheduledSessionCommand::sessionDate)
        .containsExactly(TODAY, TODAY.plusDays(7));
    assertThat(commands.getAllValues().getFirst().scheduleId()).isEqualTo(fridays.getId());
  }

  @Test
  void skipsDaysOutsideTheScheduleValidity() {
    ClassSchedule endedYesterday =
        schedule(DayOfWeek.FRIDAY, TODAY.minusMonths(1), TODAY.minusDays(1));
    ClassSchedule startsNextWeek = schedule(DayOfWeek.FRIDAY, TODAY.plusDays(7), null);
    when(loadSchedule.findActiveValidBetween(TODAY, TODAY.plusDays(7)))
        .thenReturn(List.of(endedYesterday, startsNextWeek));
    when(createSession.createFromSchedule(any())).thenReturn(ScheduledSessionOutcome.CREATED);

    GenerateSessionsResult result = service.generate(7);

    assertThat(result.created()).isEqualTo(1);
  }

  @Test
  void countsExistingConflictingAndFailedDaysSeparately() {
    ClassSchedule todayOnly = schedule(DayOfWeek.FRIDAY, TODAY, TODAY);
    ClassSchedule monday = schedule(DayOfWeek.MONDAY, TODAY, null);
    ClassSchedule saturday = schedule(DayOfWeek.SATURDAY, TODAY, null);
    when(loadSchedule.findActiveValidBetween(any(), any()))
        .thenReturn(List.of(todayOnly, monday, saturday));
    when(createSession.createFromSchedule(any()))
        .thenReturn(ScheduledSessionOutcome.ALREADY_EXISTS)
        .thenReturn(ScheduledSessionOutcome.SKIPPED_CONFLICT)
        .thenThrow(new IllegalStateException("database down"));

    GenerateSessionsResult result = service.generate(7);

    assertThat(result).isEqualTo(new GenerateSessionsResult(0, 1, 1, 1));
  }

  @Test
  void nothingToGenerateIsNotAnError() {
    when(loadSchedule.findActiveValidBetween(any(), any())).thenReturn(List.of());

    assertThat(service.generate(7)).isEqualTo(new GenerateSessionsResult(0, 0, 0, 0));
    verify(createSession, never()).createFromSchedule(any());
  }

  @Test
  void anAdminOfAnotherBranchCannotChangeASchedule() {
    ClassSchedule existing = schedule(DayOfWeek.FRIDAY, TODAY, null);
    when(loadSchedule.findById(existing.getId())).thenReturn(Optional.of(existing));
    Actor otherAdmin = actor(UUID.randomUUID(), UUID.randomUUID(), "ADMIN");

    assertThatExceptionOfType(DomainException.class)
        .isThrownBy(
            () ->
                service.update(
                    otherAdmin,
                    existing.getId(),
                    new UpdateScheduleCommand(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        DayOfWeek.MONDAY,
                        LocalTime.of(9, 0),
                        LocalTime.of(9, 40),
                        TODAY,
                        null)))
        .satisfies(ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BRANCH_SCOPE_VIOLATION));
    verify(saveSchedule, never()).save(any());
  }

  @Test
  void aMissingScheduleIsNotFound() {
    UUID id = UUID.randomUUID();
    when(loadSchedule.findById(id)).thenReturn(Optional.empty());

    assertThatExceptionOfType(DomainException.class)
        .isThrownBy(() -> service.deactivate(admin, id))
        .satisfies(ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SCHEDULE_NOT_FOUND));
  }

  @Test
  void deactivatingTwiceSavesOnce() {
    ClassSchedule existing = schedule(DayOfWeek.FRIDAY, TODAY, null);
    when(loadSchedule.findById(existing.getId())).thenReturn(Optional.of(existing));
    when(scheduleQuery.findViewById(existing.getId()))
        .thenReturn(
            Optional.of(
                new ScheduleView(
                    existing.getId(),
                    branchId,
                    existing.getClassroomId(),
                    existing.getTeacherId(),
                    existing.getSubjectId(),
                    DayOfWeek.FRIDAY,
                    LocalTime.of(9, 0),
                    LocalTime.of(9, 40),
                    TODAY,
                    null,
                    false,
                    Instant.now(),
                    Instant.now())));

    service.deactivate(admin, existing.getId());
    service.deactivate(admin, existing.getId());

    assertThat(existing.isActive()).isFalse();
    verify(saveSchedule, times(1)).save(existing);
  }
}
