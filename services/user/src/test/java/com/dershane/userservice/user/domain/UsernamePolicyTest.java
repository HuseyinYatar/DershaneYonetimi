package com.dershane.userservice.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dershane.userservice.shared.domain.DomainException;
import com.dershane.userservice.shared.domain.ErrorCode;
import org.junit.jupiter.api.Test;

class UsernamePolicyTest {

  private final UsernamePolicy policy = new UsernamePolicy();

  @Test
  void studentWithEmailUsesEmail() {
    PersonalInfo info = new PersonalInfo("Ali", "Kaya", "Ali@Example.com", null);

    assertThat(policy.forStudent(info, "MRKZ", "1042")).isEqualTo("ali@example.com");
  }

  @Test
  void studentWithoutEmailUsesBranchCodeAndNumber() {
    PersonalInfo info = new PersonalInfo("Ali", "Kaya", null, null);

    assertThat(policy.forStudent(info, "MRKZ", "1042")).isEqualTo("mrkz-1042");
  }

  @Test
  void guardianWithoutEmailUsesPhoneDigits() {
    PersonalInfo info = new PersonalInfo("Ayşe", "Kaya", null, "+90 555 111 22 33");

    assertThat(policy.forGuardian(info)).isEqualTo("905551112233");
  }

  @Test
  void staffWithoutEmailIsRejected() {
    PersonalInfo info = new PersonalInfo("Can", "Demir", null, null);

    assertThatThrownBy(() -> policy.forStaff(info, "TEACHER"))
        .isInstanceOf(DomainException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.EMAIL_REQUIRED);
  }
}
