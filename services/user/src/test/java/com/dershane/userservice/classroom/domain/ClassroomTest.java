package com.dershane.userservice.classroom.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dershane.userservice.shared.domain.DomainException;
import com.dershane.userservice.shared.domain.ErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClassroomTest {

  private final UUID branchId = UUID.randomUUID();

  @Test
  void updateLeavesNullArgumentsUnchanged() {
    Classroom classroom = Classroom.create(branchId, "12-A", 12, "2026-2027");

    classroom.update(null, "2027-2028");

    assertThat(classroom.getName()).isEqualTo("12-A");
    assertThat(classroom.getTerm()).isEqualTo("2027-2028");
  }

  @Test
  void refusesToDeactivateWhileStudentsAreEnrolled() {
    Classroom classroom = Classroom.create(branchId, "12-A", 12, "2026-2027");

    assertThatThrownBy(() -> classroom.deactivate(true))
        .isInstanceOf(DomainException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.CLASSROOM_HAS_ACTIVE_STUDENTS);
    assertThat(classroom.isActive()).isTrue();
  }

  @Test
  void deactivatingAnInactiveClassroomChangesNothing() {
    Classroom classroom = Classroom.create(branchId, "12-A", 12, "2026-2027");

    assertThat(classroom.deactivate(false)).isTrue();
    assertThat(classroom.deactivate(false)).isFalse();
  }

  @Test
  void inactiveClassroomIsRejectedBeforeTheBranchIsChecked() {
    Classroom classroom = Classroom.create(branchId, "12-A", 12, "2026-2027");
    classroom.deactivate(false);

    assertThatThrownBy(() -> classroom.requireActiveInBranch(UUID.randomUUID(), "test"))
        .isInstanceOf(DomainException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.CLASSROOM_INACTIVE);
  }

  @Test
  void classroomOfAnotherBranchIsRejected() {
    Classroom classroom = Classroom.create(branchId, "12-A", 12, "2026-2027");

    assertThatThrownBy(() -> classroom.requireActiveInBranch(UUID.randomUUID(), "test"))
        .isInstanceOf(DomainException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.BRANCH_MISMATCH);
  }
}
