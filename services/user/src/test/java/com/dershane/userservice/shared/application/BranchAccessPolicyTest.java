package com.dershane.userservice.shared.application;

import static com.dershane.userservice.support.TestJwts.actor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dershane.userservice.shared.domain.DomainException;
import com.dershane.userservice.shared.domain.ErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BranchAccessPolicyTest {

  private final BranchAccessPolicy policy = new BranchAccessPolicy();
  private final UUID ownBranch = UUID.randomUUID();
  private final UUID otherBranch = UUID.randomUUID();

  @Test
  void superAdminManagesAnyBranch() {
    Actor superAdmin = actor(UUID.randomUUID(), ownBranch, "ADMIN", "SUPER_ADMIN");

    assertThatCode(() -> policy.requireBranchAdmin(superAdmin, otherBranch, "test"))
        .doesNotThrowAnyException();
  }

  @Test
  void adminManagesOwnBranch() {
    Actor admin = actor(UUID.randomUUID(), ownBranch, "ADMIN");

    assertThatCode(() -> policy.requireBranchAdmin(admin, ownBranch, "test"))
        .doesNotThrowAnyException();
  }

  @Test
  void adminIsRejectedOnAnotherBranch() {
    Actor admin = actor(UUID.randomUUID(), ownBranch, "ADMIN");

    assertThatThrownBy(() -> policy.requireBranchAdmin(admin, otherBranch, "test"))
        .isInstanceOf(DomainException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.BRANCH_SCOPE_VIOLATION);
  }

  @Test
  void teacherIsRejectedEvenOnOwnBranch() {
    Actor teacher = actor(UUID.randomUUID(), ownBranch, "TEACHER");

    assertThatThrownBy(() -> policy.requireBranchAdmin(teacher, ownBranch, "test"))
        .isInstanceOf(DomainException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.BRANCH_SCOPE_VIOLATION);
  }

  @Test
  void adminWithoutBranchClaimIsRejected() {
    Actor admin = actor(UUID.randomUUID(), null, "ADMIN");

    assertThatThrownBy(() -> policy.requireBranchAdmin(admin, null, "test"))
        .isInstanceOf(DomainException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.BRANCH_SCOPE_VIOLATION);
  }

  @Test
  void adminWithoutBranchClaimCannotListAllBranches() {
    Actor admin = actor(UUID.randomUUID(), null, "ADMIN");

    assertThatThrownBy(() -> policy.resolveBranchFilter(admin, null, "test"))
        .isInstanceOf(DomainException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.BRANCH_SCOPE_VIOLATION);
  }

  @Test
  void branchFilterDefaultsToAdminsOwnBranch() {
    Actor admin = actor(UUID.randomUUID(), ownBranch, "ADMIN");

    assertThat(policy.resolveBranchFilter(admin, null, "test")).isEqualTo(ownBranch);
  }

  @Test
  void superAdminMayListAllBranches() {
    Actor superAdmin = actor(UUID.randomUUID(), ownBranch, "ADMIN", "SUPER_ADMIN");

    assertThat(policy.resolveBranchFilter(superAdmin, null, "test")).isNull();
  }

  @Test
  void adminMayNotListAnotherBranch() {
    Actor admin = actor(UUID.randomUUID(), ownBranch, "ADMIN");

    assertThatThrownBy(() -> policy.resolveBranchFilter(admin, otherBranch, "test"))
        .isInstanceOf(DomainException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.BRANCH_SCOPE_VIOLATION);
  }
}
