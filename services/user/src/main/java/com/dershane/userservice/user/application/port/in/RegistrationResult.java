package com.dershane.userservice.user.application.port.in;

/**
 * Result of registering a user and opening their login account.
 *
 * @param passwordSetupEmailSent true if Keycloak sent the "set your password" e-mail
 * @param temporaryPassword only for accounts without e-mail, returned once and never stored
 */
public record RegistrationResult<V>(
    V user, boolean passwordSetupEmailSent, String temporaryPassword) {

  @Override
  public String toString() {
    return "RegistrationResult[user=" + user + "]";
  }
}
