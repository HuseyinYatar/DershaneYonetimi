package com.dershane.userservice.branch.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dershane.userservice.shared.domain.DomainException;
import com.dershane.userservice.shared.domain.ErrorCode;
import org.junit.jupiter.api.Test;

class BranchTest {

  @Test
  void newBranchIsActiveAndUnsaved() {
    Branch branch = Branch.create("Kadıköy", "KDK");

    assertThat(branch.isActive()).isTrue();
    assertThat(branch.getId()).isNull();
    assertThat(branch.getVersion()).isNull();
  }

  @Test
  void deactivatesWhenNoClassroomIsActive() {
    Branch branch = Branch.create("Kadıköy", "KDK");

    assertThat(branch.deactivate(false)).isTrue();
    assertThat(branch.isActive()).isFalse();
  }

  @Test
  void refusesToDeactivateWhileClassroomsAreActive() {
    Branch branch = Branch.create("Kadıköy", "KDK");

    assertThatThrownBy(() -> branch.deactivate(true))
        .isInstanceOf(DomainException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.BRANCH_HAS_ACTIVE_CLASSROOMS);
    assertThat(branch.isActive()).isTrue();
  }

  @Test
  void deactivatingAnInactiveBranchChangesNothing() {
    Branch branch = Branch.create("Kadıköy", "KDK");
    branch.deactivate(false);

    assertThat(branch.deactivate(true)).isFalse();
  }

  @Test
  void inactiveBranchIsRejectedForNewWork() {
    Branch branch = Branch.create("Kadıköy", "KDK");
    branch.deactivate(false);

    assertThatThrownBy(() -> branch.requireActive("test"))
        .isInstanceOf(DomainException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.BRANCH_INACTIVE);
  }
}
