package com.dershane.attendanceservice.excuse.application.service;

import static com.dershane.attendanceservice.support.TestJwts.actor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dershane.attendanceservice.excuse.application.event.ExcuseApproved;
import com.dershane.attendanceservice.excuse.application.port.in.ExcuseView;
import com.dershane.attendanceservice.excuse.application.port.in.RequestExcuseCommand;
import com.dershane.attendanceservice.excuse.application.port.out.ExcuseQueryPort;
import com.dershane.attendanceservice.excuse.application.port.out.LoadExcusePort;
import com.dershane.attendanceservice.excuse.application.port.out.SaveExcusePort;
import com.dershane.attendanceservice.excuse.domain.Excuse;
import com.dershane.attendanceservice.excuse.domain.ExcuseStatus;
import com.dershane.attendanceservice.roster.application.port.in.GuardianAccessQuery;
import com.dershane.attendanceservice.shared.application.Actor;
import com.dershane.attendanceservice.shared.application.BranchAccessPolicy;
import com.dershane.attendanceservice.shared.application.BusinessCalendar;
import com.dershane.attendanceservice.shared.domain.DomainException;
import com.dershane.attendanceservice.shared.domain.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class ExcuseServiceTest {

  private static final LocalDate FROM = LocalDate.of(2026, 11, 2);
  private static final LocalDate TO = LocalDate.of(2026, 11, 4);

  @Mock private LoadExcusePort loadExcuse;
  @Mock private SaveExcusePort saveExcuse;
  @Mock private ExcuseQueryPort excuseQuery;
  @Mock private GuardianAccessQuery guardians;
  @Mock private ApplicationEventPublisher applicationEvents;

  private final UUID branchId = UUID.randomUUID();
  private final UUID studentId = UUID.randomUUID();
  private final UUID guardianId = UUID.randomUUID();
  private final Actor guardian = actor(guardianId, branchId, "GUARDIAN");
  private final Actor admin = actor(UUID.randomUUID(), branchId, "ADMIN");

  private ExcuseService service;

  @BeforeEach
  void setUp() {
    service =
        new ExcuseService(
            loadExcuse,
            saveExcuse,
            excuseQuery,
            guardians,
            new BranchAccessPolicy(),
            new BusinessCalendar(
                Clock.fixed(Instant.parse("2026-10-30T07:00:00Z"), ZoneOffset.UTC),
                "Europe/Istanbul"),
            applicationEvents);
  }

  private static void assertRejected(Runnable action, ErrorCode expected) {
    assertThatExceptionOfType(DomainException.class)
        .isThrownBy(action::run)
        .satisfies(ex -> assertThat(ex.getErrorCode()).isEqualTo(expected));
  }

  private Excuse stored(ExcuseStatus status) {
    return new Excuse(
        UUID.randomUUID(),
        0L,
        studentId,
        branchId,
        FROM,
        TO,
        "Illness",
        status,
        guardianId,
        null,
        null,
        null);
  }

  private void givenViewIsReadable(Excuse excuse) {
    when(excuseQuery.findViewById(excuse.getId()))
        .thenReturn(
            Optional.of(
                new ExcuseView(
                    excuse.getId(),
                    studentId,
                    branchId,
                    FROM,
                    TO,
                    "Illness",
                    ExcuseStatus.PENDING,
                    guardianId,
                    null,
                    null,
                    null,
                    Instant.now())));
  }

  @Test
  void aGuardianReportsAnExcuseForTheirChild() {
    when(guardians.isGuardianOf(guardianId, studentId)).thenReturn(true);
    UUID id = UUID.randomUUID();
    when(saveExcuse.save(any()))
        .thenAnswer(
            invocation -> {
              Excuse e = invocation.getArgument(0);
              return new Excuse(
                  id,
                  0L,
                  e.getStudentId(),
                  e.getBranchId(),
                  e.getFromDate(),
                  e.getToDate(),
                  e.getReason(),
                  e.getStatus(),
                  e.getRequestedBy(),
                  null,
                  null,
                  null);
            });
    givenViewIsReadable(forId(id));

    service.request(guardian, new RequestExcuseCommand(studentId, FROM, TO, "Illness"));

    ArgumentCaptor<Excuse> saved = ArgumentCaptor.forClass(Excuse.class);
    verify(saveExcuse).save(saved.capture());
    assertThat(saved.getValue().getStatus()).isEqualTo(ExcuseStatus.PENDING);
    assertThat(saved.getValue().getBranchId()).isEqualTo(branchId);
    assertThat(saved.getValue().getRequestedBy()).isEqualTo(guardianId);
  }

  @Test
  void aGuardianCannotReportForAStudentWhoIsNotTheirChild() {
    when(guardians.isGuardianOf(guardianId, studentId)).thenReturn(false);

    assertRejected(
        () -> service.request(guardian, new RequestExcuseCommand(studentId, FROM, TO, "Illness")),
        ErrorCode.GUARDIAN_NOT_LINKED);
    verify(saveExcuse, never()).save(any());
  }

  @Test
  void onlyAGuardianReportsExcuses() {
    Actor teacher = actor(UUID.randomUUID(), branchId, "TEACHER");

    assertRejected(
        () -> service.request(teacher, new RequestExcuseCommand(studentId, FROM, TO, "Illness")),
        ErrorCode.ACCESS_DENIED);
    verifyNoInteractions(guardians);
  }

  @Test
  void approvingPublishesTheInProcessEventInTheSameCall() {
    Excuse pending = stored(ExcuseStatus.PENDING);
    when(loadExcuse.findById(pending.getId())).thenReturn(Optional.of(pending));
    givenViewIsReadable(pending);

    service.approve(admin, pending.getId(), "ok");

    assertThat(pending.getStatus()).isEqualTo(ExcuseStatus.APPROVED);
    verify(saveExcuse).save(pending);
    ArgumentCaptor<ExcuseApproved> event = ArgumentCaptor.forClass(ExcuseApproved.class);
    verify(applicationEvents).publishEvent(event.capture());
    assertThat(event.getValue().excuseId()).isEqualTo(pending.getId());
    assertThat(event.getValue().studentId()).isEqualTo(studentId);
    assertThat(event.getValue().fromDate()).isEqualTo(FROM);
    assertThat(event.getValue().toDate()).isEqualTo(TO);
    assertThat(event.getValue().decidedBy()).isEqualTo(admin.id());
  }

  @Test
  void rejectingPublishesNothing() {
    Excuse pending = stored(ExcuseStatus.PENDING);
    when(loadExcuse.findById(pending.getId())).thenReturn(Optional.of(pending));
    givenViewIsReadable(pending);

    service.reject(admin, pending.getId(), "No proof");

    assertThat(pending.getStatus()).isEqualTo(ExcuseStatus.REJECTED);
    verifyNoInteractions(applicationEvents);
  }

  @Test
  void aDecidedExcuseCannotBeApprovedAgain() {
    Excuse approved = stored(ExcuseStatus.APPROVED);
    when(loadExcuse.findById(approved.getId())).thenReturn(Optional.of(approved));

    assertRejected(
        () -> service.approve(admin, approved.getId(), null), ErrorCode.EXCUSE_ALREADY_DECIDED);
    verify(saveExcuse, never()).save(any());
    verifyNoInteractions(applicationEvents);
  }

  @Test
  void anAdminOfAnotherBranchCannotDecide() {
    Excuse pending = stored(ExcuseStatus.PENDING);
    when(loadExcuse.findById(pending.getId())).thenReturn(Optional.of(pending));
    Actor otherAdmin = actor(UUID.randomUUID(), UUID.randomUUID(), "ADMIN");

    assertRejected(
        () -> service.approve(otherAdmin, pending.getId(), null), ErrorCode.BRANCH_SCOPE_VIOLATION);
    verify(saveExcuse, never()).save(any());
  }

  @Test
  void adminsListTheirBranchAndGuardiansTheirOwnExcuses() {
    when(excuseQuery.findViews(any(), any(), any(), any(), any())).thenReturn(Page.empty());

    service.list(admin, ExcuseStatus.PENDING, null, PageRequest.of(0, 20));
    verify(excuseQuery)
        .findViews(branchId, null, null, ExcuseStatus.PENDING, PageRequest.of(0, 20));

    service.list(guardian, null, studentId, PageRequest.of(0, 20));
    verify(excuseQuery).findViews(null, guardianId, studentId, null, PageRequest.of(0, 20));
  }

  @Test
  void teachersAndStudentsDoNotListExcuses() {
    Actor teacher = actor(UUID.randomUUID(), branchId, "TEACHER");

    assertRejected(
        () -> service.list(teacher, null, null, PageRequest.of(0, 20)), ErrorCode.ACCESS_DENIED);
  }

  @Test
  void coverageIsLookedUpInOneCall() {
    UUID excuseId = UUID.randomUUID();
    when(loadExcuse.findApprovedCoverage(List.of(studentId), FROM))
        .thenReturn(Map.of(studentId, excuseId));

    assertThat(service.coveredExcuses(List.of(studentId), FROM)).containsEntry(studentId, excuseId);
  }

  @Test
  void coverageOfNobodyNeedsNoQuery() {
    assertThat(service.coveredExcuses(List.of(), FROM)).isEmpty();
    verifyNoInteractions(loadExcuse);
  }

  private Excuse forId(UUID id) {
    return new Excuse(
        id,
        0L,
        studentId,
        branchId,
        FROM,
        TO,
        "Illness",
        ExcuseStatus.PENDING,
        guardianId,
        null,
        null,
        null);
  }
}
