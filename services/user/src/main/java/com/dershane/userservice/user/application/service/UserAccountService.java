package com.dershane.userservice.user.application.service;

import com.dershane.userservice.shared.application.MetricNames;
import com.dershane.userservice.shared.application.port.out.IntegrationEventPublisher;
import com.dershane.userservice.shared.domain.ErrorCode;
import com.dershane.userservice.shared.domain.Rejections;
import com.dershane.userservice.user.application.event.UserDeactivated;
import com.dershane.userservice.user.application.port.out.IdentityProfile;
import com.dershane.userservice.user.application.port.out.IdentityProviderPort;
import com.dershane.userservice.user.application.port.out.LoadUserPort;
import com.dershane.userservice.user.application.port.out.NewIdentity;
import com.dershane.userservice.user.application.port.out.SaveUserPort;
import com.dershane.userservice.user.domain.PersonalInfo;
import com.dershane.userservice.user.domain.TemporaryPasswordGenerator;
import com.dershane.userservice.user.domain.User;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Keeps the Keycloak account and the profile in this database consistent.
 *
 * <p>Keycloak is called outside the database transaction (transactions stay short). When the
 * database step fails after Keycloak was changed, the Keycloak change is undone; if undoing fails
 * too, an ERROR is logged with the account id and {@code identity.compensations{outcome=failure}}
 * is incremented, so the orphan can be found and fixed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
class UserAccountService {

  private final IdentityProviderPort identityProvider;
  private final LoadUserPort loadUser;
  private final SaveUserPort saveUser;
  private final IntegrationEventPublisher events;
  private final UserEventFactory eventFactory;
  private final ApplicationEventPublisher applicationEvents;
  private final TransactionTemplate transactionTemplate;
  private final TemporaryPasswordGenerator passwordGenerator;
  private final MeterRegistry meterRegistry;

  <T extends User> AccountCreated<T> create(NewAccount account, Function<UUID, T> profileFactory) {
    requireEmailFree(account.info(), null);
    requireUsernameFree(account.username());
    String temporaryPassword = account.info().hasEmail() ? null : passwordGenerator.generate();

    UUID accountId =
        identityProvider.createAccount(
            new NewIdentity(
                account.username(),
                account.info().email(),
                account.info().firstName(),
                account.info().lastName(),
                account.role().realmRole(),
                account.branchId(),
                temporaryPassword));

    T saved;
    try {
      saved =
          transactionTemplate.execute(
              status -> {
                T user = saveUser.save(profileFactory.apply(accountId));
                events.publish(eventFactory.userUpdated(user));
                return user;
              });
    } catch (RuntimeException ex) {
      undoAccountCreation(accountId, ex);
      throw ex;
    }

    boolean emailSent = account.info().hasEmail() && trySendPasswordSetupEmail(accountId);
    meterRegistry
        .counter(MetricNames.USER_REGISTRATIONS, "role", account.role().name())
        .increment();
    log.atInfo()
        .addKeyValue("userId", accountId)
        .addKeyValue("role", account.role())
        .addKeyValue("branchId", account.branchId())
        .addKeyValue("credentialMode", temporaryPassword == null ? "email" : "temporary")
        .addKeyValue("passwordSetupEmailSent", emailSent)
        .log("User account created");
    return new AccountCreated<>(saved, temporaryPassword, emailSent);
  }

  /**
   * Applies a profile change. Name and e-mail are mirrored to Keycloak first; role-specific fields
   * are applied by {@code otherChanges} inside the same database transaction.
   */
  <T extends User> T update(T current, PersonalInfo newInfo, Consumer<T> otherChanges) {
    PersonalInfo oldInfo = current.personalInfo();
    requireEmailFree(newInfo, current.getId());
    boolean identityChanged = identityFieldsChanged(oldInfo, newInfo);
    if (identityChanged) {
      identityProvider.updateProfile(current.getId(), identityProfile(newInfo));
    } else {
      log.atDebug()
          .addKeyValue("userId", current.getId())
          .log("Name and e-mail unchanged; Keycloak not called");
    }
    try {
      T saved =
          transactionTemplate.execute(
              status -> {
                T user = reload(current);
                user.applyPersonalInfo(newInfo);
                otherChanges.accept(user);
                T flushed = saveUser.save(user);
                events.publish(eventFactory.userUpdated(flushed));
                return flushed;
              });
      log.atInfo().addKeyValue("userId", current.getId()).log("User profile updated");
      return saved;
    } catch (RuntimeException ex) {
      if (identityChanged) {
        undoProfileUpdate(current.getId(), oldInfo);
      }
      throw ex;
    }
  }

  /**
   * Disables the login first (a deactivated user must not be able to log in), then marks the
   * profile inactive. {@link UserDeactivated} lets other modules close what depends on the user (a
   * student's classroom membership) inside the same transaction.
   */
  <T extends User> T deactivate(T current) {
    identityProvider.setEnabled(current.getId(), false);
    try {
      T saved =
          transactionTemplate.execute(
              status -> {
                T user = reload(current);
                user.deactivate();
                applicationEvents.publishEvent(new UserDeactivated(user.getId(), user.getRole()));
                T flushed = saveUser.save(user);
                events.publish(eventFactory.userUpdated(flushed));
                return flushed;
              });
      meterRegistry
          .counter(MetricNames.USER_DEACTIVATIONS, "role", current.getRole().name())
          .increment();
      log.atInfo()
          .addKeyValue("userId", current.getId())
          .addKeyValue("role", current.getRole())
          .log("User deactivated");
      return saved;
    } catch (RuntimeException ex) {
      undoDisable(current.getId(), ex);
      throw ex;
    }
  }

  String resetTemporaryPassword(User user) {
    String password = passwordGenerator.generate();
    identityProvider.setTemporaryPassword(user.getId(), password);
    log.atInfo().addKeyValue("userId", user.getId()).log("Temporary password reset by admin");
    return password;
  }

  void sendPasswordSetupEmail(User user) {
    identityProvider.sendPasswordSetupEmail(user.getId());
  }

  private boolean trySendPasswordSetupEmail(UUID accountId) {
    try {
      identityProvider.sendPasswordSetupEmail(accountId);
      return true;
    } catch (RuntimeException ex) {
      log.atWarn()
          .addKeyValue("userId", accountId)
          .addKeyValue("exception", ex.getClass().getSimpleName())
          .log("Account created but the password setup e-mail failed; it can be resent");
      return false;
    }
  }

  private void requireEmailFree(PersonalInfo info, UUID ownId) {
    if (info.hasEmail() && emailTaken(info.email(), ownId)) {
      throw Rejections.reject(
          log,
          ErrorCode.EMAIL_TAKEN,
          "E-mail address already belongs to another user",
          "userId",
          ownId);
    }
  }

  private boolean emailTaken(String email, UUID ownId) {
    return ownId == null
        ? loadUser.existsByEmail(email)
        : loadUser.existsByEmailAndIdNot(email, ownId);
  }

  private void requireUsernameFree(String username) {
    if (loadUser.existsByUsername(username)) {
      throw Rejections.reject(log, ErrorCode.USERNAME_TAKEN, "Username already in use");
    }
  }

  @SuppressWarnings("unchecked")
  private <T extends User> T reload(T current) {
    return (T)
        loadUser
            .findById(current.getId())
            .orElseThrow(
                () ->
                    Rejections.reject(
                        log,
                        ErrorCode.USER_NOT_FOUND,
                        "User disappeared during update",
                        "userId",
                        current.getId()));
  }

  private static boolean identityFieldsChanged(PersonalInfo oldInfo, PersonalInfo newInfo) {
    return !Objects.equals(oldInfo.firstName(), newInfo.firstName())
        || !Objects.equals(oldInfo.lastName(), newInfo.lastName())
        || !Objects.equals(oldInfo.email(), newInfo.email());
  }

  private static IdentityProfile identityProfile(PersonalInfo info) {
    return new IdentityProfile(info.email(), info.firstName(), info.lastName());
  }

  private void undoAccountCreation(UUID accountId, RuntimeException cause) {
    log.atWarn()
        .addKeyValue("userId", accountId)
        .addKeyValue("exception", cause.getClass().getSimpleName())
        .log("Saving the profile failed after Keycloak account creation; removing the account");
    compensate(accountId, "deleteAccount", () -> identityProvider.deleteAccount(accountId));
  }

  private void undoProfileUpdate(UUID userId, PersonalInfo oldInfo) {
    log.atWarn()
        .addKeyValue("userId", userId)
        .log("Saving the profile failed after Keycloak update; restoring the previous values");
    compensate(
        userId,
        "restoreProfile",
        () -> identityProvider.updateProfile(userId, identityProfile(oldInfo)));
  }

  private void undoDisable(UUID userId, RuntimeException cause) {
    log.atWarn()
        .addKeyValue("userId", userId)
        .addKeyValue("exception", cause.getClass().getSimpleName())
        .log("Deactivating the profile failed after disabling the login; re-enabling it");
    compensate(userId, "reEnable", () -> identityProvider.setEnabled(userId, true));
  }

  private void compensate(UUID accountId, String action, Runnable compensation) {
    try {
      compensation.run();
      meterRegistry
          .counter(MetricNames.IDENTITY_COMPENSATIONS, "action", action, "outcome", "success")
          .increment();
    } catch (RuntimeException ex) {
      meterRegistry
          .counter(MetricNames.IDENTITY_COMPENSATIONS, "action", action, "outcome", "failure")
          .increment();
      log.atError()
          .addKeyValue("userId", accountId)
          .addKeyValue("action", action)
          .addKeyValue("exception", ex.getClass().getSimpleName())
          .log("Compensation failed: Keycloak and the profile database are out of sync");
    }
  }
}
