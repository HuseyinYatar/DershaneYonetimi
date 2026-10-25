package com.dershane.userservice.user.application.port.out;

import java.util.UUID;

/**
 * Data for a new login account. Contains personal data and possibly a temporary password: {@code
 * toString} is masked.
 *
 * @param email may be null (students and guardians without e-mail)
 * @param temporaryPassword null when the password is set through the e-mail flow
 */
public record NewIdentity(
    String username,
    String email,
    String firstName,
    String lastName,
    String realmRole,
    UUID branchId,
    String temporaryPassword) {

  @Override
  public String toString() {
    return "NewIdentity[realmRole=" + realmRole + ", branchId=" + branchId + "]";
  }
}
