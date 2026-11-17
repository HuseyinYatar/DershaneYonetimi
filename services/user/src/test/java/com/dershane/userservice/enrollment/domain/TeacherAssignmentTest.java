package com.dershane.userservice.enrollment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dershane.userservice.shared.domain.DomainException;
import com.dershane.userservice.shared.domain.ErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TeacherAssignmentTest {

  private final TeacherAssignment saved =
      new TeacherAssignment(
          UUID.randomUUID(), 3L, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 0);

  @Test
  void newAssignmentNeedsNoHoursYet() {
    TeacherAssignment created =
        TeacherAssignment.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    assertThat(created.getWeeklyHours()).isZero();
  }

  @Test
  void changingHoursKeepsIdentityAndVersion() {
    TeacherAssignment changed = saved.withWeeklyHours(4);

    assertThat(changed.getWeeklyHours()).isEqualTo(4);
    assertThat(changed.getId()).isEqualTo(saved.getId());
    assertThat(changed.getVersion()).isEqualTo(3L);
    assertThat(changed.getClassroomId()).isEqualTo(saved.getClassroomId());
  }

  @Test
  void rejectsHoursOutsideZeroToForty() {
    assertThatThrownBy(() -> saved.withWeeklyHours(-1))
        .isInstanceOf(DomainException.class)
        .extracting(e -> ((DomainException) e).getErrorCode())
        .isEqualTo(ErrorCode.WEEKLY_HOURS_OUT_OF_RANGE);
    assertThatThrownBy(() -> saved.withWeeklyHours(TeacherAssignment.MAX_WEEKLY_HOURS + 1))
        .isInstanceOf(DomainException.class);
  }
}
