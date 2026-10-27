package com.dershane.userservice.user.domain;

import com.dershane.userservice.shared.domain.ErrorCode;
import com.dershane.userservice.shared.domain.Rejections;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;

/**
 * Login names. E-mail is preferred; students without e-mail log in as {@code branchcode-number} and
 * guardians without e-mail with their phone number. The username never changes afterwards (Keycloak
 * still lets users log in with a later e-mail address).
 */
@Slf4j
public class UsernamePolicy {

  public String forStudent(PersonalInfo info, String branchCode, String studentNumber) {
    if (info.hasEmail()) {
      return info.email();
    }
    return (branchCode + "-" + studentNumber).toLowerCase(Locale.ROOT);
  }

  public String forGuardian(PersonalInfo info) {
    if (info.hasEmail()) {
      return info.email();
    }
    if (!info.hasPhone()) {
      throw Rejections.reject(
          log, ErrorCode.PHONE_REQUIRED, "Guardian without e-mail must have a phone number");
    }
    return info.phone().replace("+", "");
  }

  /** Teachers and admins always have an e-mail address. */
  public String forStaff(PersonalInfo info, String role) {
    if (!info.hasEmail()) {
      throw Rejections.reject(
          log, ErrorCode.EMAIL_REQUIRED, "Staff account requires an e-mail address", "role", role);
    }
    return info.email();
  }
}
