package com.dershane.userservice.user.adapter.in.web.dto;

import com.dershane.userservice.shared.adapter.in.web.idempotency.ReplaySafe;
import com.dershane.userservice.user.application.port.in.RegistrationResult;
import java.util.function.Function;

/**
 * Result of creating an account.
 *
 * @param passwordSetupEmailSent true if Keycloak sent the "set your password" e-mail
 * @param temporaryPassword only for accounts without e-mail, returned once and never stored
 * @param temporaryPasswordAlreadyIssued true on an idempotent replay of a creation that issued a
 *     temporary password; use {@code POST /users/{id}/temporary-password} for a new one
 */
public record UserCreatedResponse<T>(
    T user,
    boolean passwordSetupEmailSent,
    String temporaryPassword,
    boolean temporaryPasswordAlreadyIssued)
    implements ReplaySafe {

  public static <V, R> UserCreatedResponse<R> of(
      RegistrationResult<V> result, Function<V, R> toResponse) {
    return new UserCreatedResponse<>(
        toResponse.apply(result.user()),
        result.passwordSetupEmailSent(),
        result.temporaryPassword(),
        false);
  }

  @Override
  public Object forReplay() {
    return temporaryPassword == null
        ? this
        : new UserCreatedResponse<>(user, passwordSetupEmailSent, null, true);
  }

  @Override
  public String toString() {
    return "UserCreatedResponse[user=" + user + "]";
  }
}
