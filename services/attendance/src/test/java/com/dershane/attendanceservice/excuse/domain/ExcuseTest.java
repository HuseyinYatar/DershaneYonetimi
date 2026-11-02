package com.dershane.attendanceservice.excuse.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.dershane.attendanceservice.shared.domain.DomainException;
import com.dershane.attendanceservice.shared.domain.ErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExcuseTest {

  private static final LocalDate FROM = LocalDate.of(2026, 11, 2);
  private static final LocalDate TO = LocalDate.of(2026, 11, 4);

  private final UUID admin = UUID.randomUUID();

  private Excuse pending() {
    return Excuse.request(
        UUID.randomUUID(), UUID.randomUUID(), FROM, TO, "Doctor appointment", UUID.randomUUID());
  }

  private static void assertRejected(Runnable action, ErrorCode expected) {
    assertThatExceptionOfType(DomainException.class)
        .isThrownBy(action::run)
        .satisfies(ex -> assertThat(ex.getErrorCode()).isEqualTo(expected));
  }

  @Test
  void aNewExcuseIsPending() {
    Excuse excuse = pending();

    assertThat(excuse.getStatus()).isEqualTo(ExcuseStatus.PENDING);
    assertThat(excuse.covers(FROM)).isFalse();
  }

  @Test
  void theRangeMustNotEndBeforeItStarts() {
    assertRejected(
        () ->
            Excuse.request(
                UUID.randomUUID(), UUID.randomUUID(), TO, FROM, "Illness", UUID.randomUUID()),
        ErrorCode.INVALID_DATE_RANGE);
  }

  @Test
  void aReasonIsRequired() {
    assertRejected(
        () ->
            Excuse.request(UUID.randomUUID(), UUID.randomUUID(), FROM, TO, "  ", UUID.randomUUID()),
        ErrorCode.REASON_REQUIRED);
  }

  @Test
  void anApprovedExcuseCoversItsRangeInclusive() {
    Excuse excuse = pending();

    excuse.approve(admin, Instant.parse("2026-10-30T08:00:00Z"), " ok ");

    assertThat(excuse.getStatus()).isEqualTo(ExcuseStatus.APPROVED);
    assertThat(excuse.getDecidedBy()).isEqualTo(admin);
    assertThat(excuse.getDecisionNote()).isEqualTo("ok");
    assertThat(excuse.covers(FROM.minusDays(1))).isFalse();
    assertThat(excuse.covers(FROM)).isTrue();
    assertThat(excuse.covers(TO)).isTrue();
    assertThat(excuse.covers(TO.plusDays(1))).isFalse();
  }

  @Test
  void aRejectedExcuseCoversNothing() {
    Excuse excuse = pending();

    excuse.reject(admin, Instant.now(), null);

    assertThat(excuse.getStatus()).isEqualTo(ExcuseStatus.REJECTED);
    assertThat(excuse.getDecisionNote()).isNull();
    assertThat(excuse.covers(FROM)).isFalse();
  }

  @Test
  void aDecidedExcuseCannotBeDecidedAgain() {
    Excuse approved = pending();
    approved.approve(admin, Instant.now(), null);
    Excuse rejected = pending();
    rejected.reject(admin, Instant.now(), null);

    assertRejected(
        () -> approved.reject(admin, Instant.now(), null), ErrorCode.EXCUSE_ALREADY_DECIDED);
    assertRejected(
        () -> rejected.approve(admin, Instant.now(), null), ErrorCode.EXCUSE_ALREADY_DECIDED);
  }

  @Test
  void toStringHidesTheReason() {
    assertThat(pending().toString()).doesNotContain("Doctor");
  }
}
