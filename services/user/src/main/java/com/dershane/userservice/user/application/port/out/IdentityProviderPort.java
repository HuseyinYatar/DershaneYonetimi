package com.dershane.userservice.user.application.port.out;

import java.util.UUID;

/**
 * Login accounts live in the identity provider (Keycloak); this service owns the profiles. The
 * account id returned by {@link #createAccount} becomes the user's id here.
 *
 * <p>Implementations throw {@code DomainException} with IDENTITY_PROVIDER_* codes when the provider
 * fails, after logging the failure.
 */
public interface IdentityProviderPort {

  UUID createAccount(NewIdentity identity);

  void updateProfile(UUID accountId, IdentityProfile profile);

  void setEnabled(UUID accountId, boolean enabled);

  /** Only used to undo {@link #createAccount} when saving the profile fails. */
  void deleteAccount(UUID accountId);

  /** Sends the "set your password" e-mail. The account must have an e-mail address. */
  void sendPasswordSetupEmail(UUID accountId);

  /** For accounts without e-mail: the user must change it on first login. */
  void setTemporaryPassword(UUID accountId, String temporaryPassword);
}
