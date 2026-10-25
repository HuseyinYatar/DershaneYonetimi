package com.dershane.userservice.user.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PersonalInfoTest {

  @Test
  void normalizesEmailAndPhone() {
    PersonalInfo info =
        new PersonalInfo(" Ali ", "Kaya ", " Ali@Example.COM ", "+90 (555) 111-22-33");

    assertThat(info.firstName()).isEqualTo("Ali");
    assertThat(info.lastName()).isEqualTo("Kaya");
    assertThat(info.email()).isEqualTo("ali@example.com");
    assertThat(info.phone()).isEqualTo("+905551112233");
  }

  @Test
  void blankContactDetailsBecomeNull() {
    PersonalInfo info = new PersonalInfo("Ali", "Kaya", "  ", "");

    assertThat(info.hasEmail()).isFalse();
    assertThat(info.hasPhone()).isFalse();
  }

  @Test
  void mergeKeepsCurrentValuesForNullFields() {
    PersonalInfo current = new PersonalInfo("Ali", "Kaya", "ali@example.com", "05551112233");

    PersonalInfo merged = current.merge(new PersonalInfo(null, "Yılmaz", null, null));

    assertThat(merged)
        .isEqualTo(new PersonalInfo("Ali", "Yılmaz", "ali@example.com", "05551112233"));
  }
}
