package com.dershane.taskservice.roster.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.dershane.taskservice.shared.domain.DomainException;
import com.dershane.taskservice.shared.domain.ErrorCode;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MembershipReplicaTest {

  private static final LocalDate START = LocalDate.of(2026, 9, 14);

  private MembershipReplica open() {
    return MembershipReplica.open(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        START,
        "1042",
        "Ali Kaya");
  }

  @Test
  void anOpenMembershipCoversEveryDayFromItsStart() {
    MembershipReplica membership = open();

    assertThat(membership.activeOn(START.minusDays(1))).isFalse();
    assertThat(membership.activeOn(START)).isTrue();
    assertThat(membership.activeOn(START.plusYears(1))).isTrue();
  }

  @Test
  void aClosedMembershipCoversItsEndDateToo() {
    MembershipReplica membership = open();

    assertThat(membership.close(START.plusDays(10))).isTrue();

    assertThat(membership.activeOn(START.plusDays(10))).isTrue();
    assertThat(membership.activeOn(START.plusDays(11))).isFalse();
  }

  @Test
  void closingTwiceOnTheSameDayChangesNothing() {
    MembershipReplica membership = open();
    membership.close(START.plusDays(10));

    assertThat(membership.close(START.plusDays(10))).isFalse();
  }

  @Test
  void aMembershipCannotEndBeforeItStarted() {
    assertThatExceptionOfType(DomainException.class)
        .isThrownBy(() -> open().close(START.minusDays(1)))
        .satisfies(ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_DATE_RANGE));
  }

  @Test
  void toStringHidesNameAndNumber() {
    assertThat(open().toString()).doesNotContain("Ali").doesNotContain("1042");
  }
}
