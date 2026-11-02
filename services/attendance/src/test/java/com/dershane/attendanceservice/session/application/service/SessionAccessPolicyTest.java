package com.dershane.attendanceservice.session.application.service;

import static com.dershane.attendanceservice.support.TestJwts.actor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.dershane.attendanceservice.session.domain.ClassSession;
import com.dershane.attendanceservice.session.domain.SessionStatus;
import com.dershane.attendanceservice.shared.application.Actor;
import com.dershane.attendanceservice.shared.application.BranchAccessPolicy;
import com.dershane.attendanceservice.shared.domain.DomainException;
import com.dershane.attendanceservice.shared.domain.ErrorCode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SessionAccessPolicyTest {

  private final UUID branchId = UUID.randomUUID();
  private final UUID otherBranchId = UUID.randomUUID();
  private final UUID teacherId = UUID.randomUUID();
  private final SessionAccessPolicy policy = new SessionAccessPolicy(new BranchAccessPolicy());

  private final ClassSession session =
      new ClassSession(
          UUID.randomUUID(),
          0L,
          null,
          branchId,
          UUID.randomUUID(),
          teacherId,
          UUID.randomUUID(),
          LocalDate.of(2026, 10, 30),
          LocalTime.of(9, 0),
          LocalTime.of(9, 40),
          SessionStatus.PLANNED,
          null,
          null);

  private void assertDenied(Actor actor, ErrorCode expected) {
    assertThatExceptionOfType(DomainException.class)
        .isThrownBy(() -> policy.requireStaffOf(actor, session, "test"))
        .satisfies(ex -> assertThat(ex.getErrorCode()).isEqualTo(expected));
  }

  @Test
  void theSessionsTeacherHasAccess() {
    assertThatCode(
            () -> policy.requireStaffOf(actor(teacherId, branchId, "TEACHER"), session, "test"))
        .doesNotThrowAnyException();
  }

  @Test
  void anotherTeacherIsRefused() {
    assertDenied(actor(UUID.randomUUID(), branchId, "TEACHER"), ErrorCode.NOT_SESSION_TEACHER);
  }

  @Test
  void anAdminOfTheBranchHasAccess() {
    assertThatCode(
            () ->
                policy.requireStaffOf(actor(UUID.randomUUID(), branchId, "ADMIN"), session, "test"))
        .doesNotThrowAnyException();
  }

  @Test
  void anAdminOfAnotherBranchIsRefused() {
    assertDenied(
        actor(UUID.randomUUID(), otherBranchId, "ADMIN"), ErrorCode.BRANCH_SCOPE_VIOLATION);
  }

  @Test
  void aSuperAdminHasAccessEverywhere() {
    assertThatCode(
            () ->
                policy.requireStaffOf(
                    actor(UUID.randomUUID(), otherBranchId, "ADMIN", "SUPER_ADMIN"),
                    session,
                    "test"))
        .doesNotThrowAnyException();
  }

  @Test
  void studentsAndGuardiansAreRefused() {
    assertDenied(actor(UUID.randomUUID(), branchId, "STUDENT"), ErrorCode.ACCESS_DENIED);
    assertDenied(actor(UUID.randomUUID(), branchId, "GUARDIAN"), ErrorCode.ACCESS_DENIED);
  }

  @Test
  void onlyAnAdminOfTheBranchOrASuperAdminActsAsAdmin() {
    assertThat(policy.actsAsAdmin(actor(UUID.randomUUID(), branchId, "ADMIN"), session)).isTrue();
    assertThat(policy.actsAsAdmin(actor(UUID.randomUUID(), otherBranchId, "ADMIN"), session))
        .isFalse();
    assertThat(
            policy.actsAsAdmin(
                actor(UUID.randomUUID(), otherBranchId, "ADMIN", "SUPER_ADMIN"), session))
        .isTrue();
    assertThat(policy.actsAsAdmin(actor(teacherId, branchId, "TEACHER"), session)).isFalse();
  }

  @Test
  void anAdminWithoutABranchClaimIsNotAnAdminOfAnyBranch() {
    Actor noBranch = new Actor(UUID.randomUUID(), java.util.Set.of("ADMIN"), null);

    assertThat(policy.actsAsAdmin(noBranch, session)).isFalse();
  }
}
