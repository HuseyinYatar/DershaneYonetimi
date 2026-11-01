package com.dershane.attendanceservice.attendance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.dershane.attendanceservice.shared.domain.DomainException;
import com.dershane.attendanceservice.shared.domain.ErrorCode;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AttendancePolicyTest {

  private final UUID sessionId = UUID.randomUUID();
  private final UUID ali = UUID.randomUUID();
  private final UUID ayse = UUID.randomUUID();
  private final UUID excuseId = UUID.randomUUID();

  @ParameterizedTest
  @EnumSource(
      value = AttendanceStatus.class,
      names = {"ABSENT", "EXCUSED"})
  void anApprovedExcuseTurnsMissingIntoExcusedWithTheExcuse(AttendanceStatus requested) {
    AttendancePolicy.Outcome outcome = AttendancePolicy.resolve(requested, excuseId);

    assertThat(outcome.status()).isEqualTo(AttendanceStatus.EXCUSED);
    assertThat(outcome.excuseId()).isEqualTo(excuseId);
  }

  @ParameterizedTest
  @EnumSource(
      value = AttendanceStatus.class,
      names = {"PRESENT", "LATE"})
  void anApprovedExcuseDoesNotChangePresentOrLate(AttendanceStatus requested) {
    AttendancePolicy.Outcome outcome = AttendancePolicy.resolve(requested, excuseId);

    assertThat(outcome.status()).isEqualTo(requested);
    assertThat(outcome.excuseId()).isNull();
  }

  @ParameterizedTest
  @EnumSource(AttendanceStatus.class)
  void withoutAnExcuseTheTeacherMarkStands(AttendanceStatus requested) {
    AttendancePolicy.Outcome outcome = AttendancePolicy.resolve(requested, null);

    assertThat(outcome).isEqualTo(new AttendancePolicy.Outcome(requested, null));
  }

  @Test
  void theListMustNameEveryStudentExactlyOnce() {
    assertThatCode(
            () ->
                AttendancePolicy.requireExactRoster(
                    sessionId, Set.of(ali, ayse), List.of(ayse, ali)))
        .doesNotThrowAnyException();
  }

  @Test
  void aForgottenStudentIsARosterMismatch() {
    assertRosterMismatch(Set.of(ali, ayse), List.of(ali));
  }

  @Test
  void anUnknownStudentIsARosterMismatch() {
    assertRosterMismatch(Set.of(ali), List.of(ali, UUID.randomUUID()));
  }

  @Test
  void aDuplicatedStudentIsARosterMismatch() {
    assertRosterMismatch(Set.of(ali, ayse), List.of(ali, ali, ayse));
  }

  @Test
  void anEmptyRosterAcceptsAnEmptyList() {
    assertThatCode(() -> AttendancePolicy.requireExactRoster(sessionId, Set.of(), List.of()))
        .doesNotThrowAnyException();
  }

  @Test
  void aTeacherMayCorrectUntilTheWindowEnds() {
    LocalDate sessionDay = LocalDate.of(2026, 10, 20);

    assertThatCode(
            () ->
                AttendancePolicy.requireWithinCorrectionWindow(
                    sessionId, sessionDay, LocalDate.of(2026, 10, 27), 7))
        .doesNotThrowAnyException();
    assertThatExceptionOfType(DomainException.class)
        .isThrownBy(
            () ->
                AttendancePolicy.requireWithinCorrectionWindow(
                    sessionId, sessionDay, LocalDate.of(2026, 10, 28), 7))
        .satisfies(
            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CORRECTION_WINDOW_CLOSED));
  }

  private void assertRosterMismatch(Set<UUID> roster, List<UUID> submitted) {
    assertThatExceptionOfType(DomainException.class)
        .isThrownBy(() -> AttendancePolicy.requireExactRoster(sessionId, roster, submitted))
        .satisfies(ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ROSTER_MISMATCH));
  }
}
