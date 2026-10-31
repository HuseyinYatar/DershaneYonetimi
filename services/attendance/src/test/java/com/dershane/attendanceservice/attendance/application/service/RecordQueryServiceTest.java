package com.dershane.attendanceservice.attendance.application.service;

import static com.dershane.attendanceservice.support.TestJwts.actor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dershane.attendanceservice.attendance.application.port.in.AttendanceSummaryView;
import com.dershane.attendanceservice.attendance.application.port.out.RecordFilter;
import com.dershane.attendanceservice.attendance.application.port.out.RecordQueryPort;
import com.dershane.attendanceservice.attendance.domain.AttendanceStatus;
import com.dershane.attendanceservice.roster.application.port.in.GuardianAccessQuery;
import com.dershane.attendanceservice.shared.application.Actor;
import com.dershane.attendanceservice.shared.application.BranchAccessPolicy;
import com.dershane.attendanceservice.shared.domain.DomainException;
import com.dershane.attendanceservice.shared.domain.ErrorCode;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class RecordQueryServiceTest {

  private static final Pageable PAGE = PageRequest.of(0, 20);
  private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
  private static final LocalDate TO = LocalDate.of(2026, 10, 31);

  @Mock private RecordQueryPort recordQuery;
  @Mock private GuardianAccessQuery guardians;
  @Captor private ArgumentCaptor<RecordFilter> filterCaptor;

  private final UUID branchId = UUID.randomUUID();
  private final UUID studentId = UUID.randomUUID();
  private final UUID classroomId = UUID.randomUUID();

  private RecordQueryService service() {
    return new RecordQueryService(recordQuery, guardians, new BranchAccessPolicy());
  }

  private RecordFilter listAs(Actor actor, UUID requestedStudent) {
    when(recordQuery.findViews(any(), any())).thenReturn(Page.empty());
    service().list(actor, requestedStudent, classroomId, FROM, TO, PAGE);
    verify(recordQuery).findViews(filterCaptor.capture(), any());
    return filterCaptor.getValue();
  }

  private void assertDenied(Runnable action, ErrorCode expected) {
    assertThatExceptionOfType(DomainException.class)
        .isThrownBy(action::run)
        .satisfies(ex -> assertThat(ex.getErrorCode()).isEqualTo(expected));
    verify(recordQuery, never()).findViews(any(), any());
  }

  @Test
  void aStudentSeesOnlyTheirOwnRecords() {
    RecordFilter filter = listAs(actor(studentId, branchId, "STUDENT"), null);

    assertThat(filter.studentId()).isEqualTo(studentId);
    assertThat(filter.branchId()).isNull();
    assertThat(filter.teacherId()).isNull();
    assertThat(filter.classroomId()).isEqualTo(classroomId);
    assertThat(filter.from()).isEqualTo(FROM);
    assertThat(filter.to()).isEqualTo(TO);
  }

  @Test
  void aStudentCannotAskForAnotherStudent() {
    Actor student = actor(studentId, branchId, "STUDENT");

    assertDenied(
        () -> service().list(student, UUID.randomUUID(), null, null, null, PAGE),
        ErrorCode.ACCESS_DENIED);
  }

  @Test
  void aGuardianSeesTheirChildButNobodyElse() {
    UUID guardianId = UUID.randomUUID();
    Actor guardian = actor(guardianId, branchId, "GUARDIAN");
    when(guardians.isGuardianOf(guardianId, studentId)).thenReturn(true);

    RecordFilter filter = listAs(guardian, studentId);

    assertThat(filter.studentId()).isEqualTo(studentId);
  }

  @Test
  void aGuardianCannotSeeAStudentWhoIsNotTheirChild() {
    UUID guardianId = UUID.randomUUID();
    Actor guardian = actor(guardianId, branchId, "GUARDIAN");
    when(guardians.isGuardianOf(guardianId, studentId)).thenReturn(false);

    assertDenied(
        () -> service().list(guardian, studentId, null, null, null, PAGE),
        ErrorCode.GUARDIAN_NOT_LINKED);
  }

  @Test
  void aGuardianMustNameTheStudent() {
    Actor guardian = actor(UUID.randomUUID(), branchId, "GUARDIAN");

    assertDenied(
        () -> service().list(guardian, null, null, null, null, PAGE),
        ErrorCode.STUDENT_FILTER_REQUIRED);
  }

  @Test
  void aTeacherSeesOnlyTheSessionsTheyTeach() {
    UUID teacherId = UUID.randomUUID();

    RecordFilter filter = listAs(actor(teacherId, branchId, "TEACHER"), studentId);

    assertThat(filter.teacherId()).isEqualTo(teacherId);
    assertThat(filter.studentId()).isEqualTo(studentId);
    assertThat(filter.branchId()).isNull();
  }

  @Test
  void aBranchAdminIsConfinedToTheirBranch() {
    RecordFilter filter = listAs(actor(UUID.randomUUID(), branchId, "ADMIN"), null);

    assertThat(filter.branchId()).isEqualTo(branchId);
    assertThat(filter.teacherId()).isNull();
  }

  @Test
  void aSuperAdminSeesEveryBranch() {
    RecordFilter filter =
        listAs(actor(UUID.randomUUID(), branchId, "ADMIN", "SUPER_ADMIN"), studentId);

    assertThat(filter.branchId()).isNull();
  }

  @Test
  void anUnknownRoleSeesNothing() {
    Actor nobody = actor(UUID.randomUUID(), branchId);

    assertDenied(
        () -> service().list(nobody, null, null, null, null, PAGE), ErrorCode.ACCESS_DENIED);
  }

  @Test
  void theSummaryCountsEveryStatus() {
    Map<AttendanceStatus, Long> counts = new EnumMap<>(AttendanceStatus.class);
    counts.put(AttendanceStatus.PRESENT, 18L);
    counts.put(AttendanceStatus.ABSENT, 2L);
    counts.put(AttendanceStatus.EXCUSED, 1L);
    when(recordQuery.countByStatus(any())).thenReturn(counts);

    AttendanceSummaryView summary =
        service().summarize(actor(studentId, branchId, "STUDENT"), null, FROM, TO);

    assertThat(summary.studentId()).isEqualTo(studentId);
    assertThat(summary.present()).isEqualTo(18);
    assertThat(summary.absent()).isEqualTo(2);
    assertThat(summary.late()).isZero();
    assertThat(summary.excused()).isEqualTo(1);
    assertThat(summary.total()).isEqualTo(21);
  }

  @Test
  void aSummaryForStaffNeedsAStudent() {
    Actor teacher = actor(UUID.randomUUID(), branchId, "TEACHER");

    assertThatExceptionOfType(DomainException.class)
        .isThrownBy(() -> service().summarize(teacher, null, FROM, TO))
        .satisfies(
            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.STUDENT_FILTER_REQUIRED));
    verify(recordQuery, never()).countByStatus(any());
  }

  @Test
  void listingPassesThePageThrough() {
    when(recordQuery.findViews(any(), any()))
        .thenReturn(new PageImpl<>(java.util.List.of(), PAGE, 0));

    assertThat(
            service()
                .list(actor(UUID.randomUUID(), branchId, "ADMIN"), null, null, null, null, PAGE)
                .getTotalElements())
        .isZero();
  }
}
