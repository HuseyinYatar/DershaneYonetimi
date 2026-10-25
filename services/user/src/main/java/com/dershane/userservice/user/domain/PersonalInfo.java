package com.dershane.userservice.user.domain;

import java.util.Locale;

/**
 * Name and contact details of a user. Personal data: {@link #toString()} is masked so it can't leak
 * into logs by accident.
 */
public record PersonalInfo(String firstName, String lastName, String email, String phone) {

  public PersonalInfo {
    firstName = firstName == null ? null : firstName.strip();
    lastName = lastName == null ? null : lastName.strip();
    email = email == null || email.isBlank() ? null : email.strip().toLowerCase(Locale.ROOT);
    phone = phone == null || phone.isBlank() ? null : normalizePhone(phone);
  }

  public boolean hasEmail() {
    return email != null;
  }

  public boolean hasPhone() {
    return phone != null;
  }

  /** Applies the non-null fields of {@code change}; null means "keep the current value". */
  public PersonalInfo merge(PersonalInfo change) {
    return new PersonalInfo(
        change.firstName() != null ? change.firstName() : firstName,
        change.lastName() != null ? change.lastName() : lastName,
        change.email() != null ? change.email() : email,
        change.phone() != null ? change.phone() : phone);
  }

  /** Keeps a leading {@code +} and digits; spaces, dashes and parentheses are dropped. */
  static String normalizePhone(String raw) {
    String stripped = raw.strip();
    String digits = stripped.replaceAll("[^0-9]", "");
    return stripped.startsWith("+") ? "+" + digits : digits;
  }

  @Override
  public String toString() {
    return "PersonalInfo[masked]";
  }
}
