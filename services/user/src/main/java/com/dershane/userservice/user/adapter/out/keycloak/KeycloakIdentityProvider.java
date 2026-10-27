package com.dershane.userservice.user.adapter.out.keycloak;

import com.dershane.userservice.shared.application.Actor;
import com.dershane.userservice.shared.domain.DomainException;
import com.dershane.userservice.shared.domain.ErrorCode;
import com.dershane.userservice.shared.domain.Rejections;
import com.dershane.userservice.user.application.port.out.IdentityProfile;
import com.dershane.userservice.user.application.port.out.IdentityProviderPort;
import com.dershane.userservice.user.application.port.out.NewIdentity;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Keycloak Admin REST API adapter.
 *
 * <p>Response bodies are never logged: Keycloak error messages can echo usernames or e-mail
 * addresses. Only the operation, account id and HTTP status are logged.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeycloakIdentityProvider implements IdentityProviderPort {

  private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
      new ParameterizedTypeReference<>() {};

  private final RestClient keycloakAdminRestClient;
  private final KeycloakProperties properties;
  private final Map<String, Map<String, Object>> realmRoleCache = new ConcurrentHashMap<>();

  @Override
  public UUID createAccount(NewIdentity identity) {
    Map<String, Object> representation = new HashMap<>();
    representation.put("username", identity.username());
    representation.put("firstName", identity.firstName());
    representation.put("lastName", identity.lastName());
    representation.put("enabled", true);
    representation.put(
        "attributes", Map.of(Actor.BRANCH_CLAIM, List.of(identity.branchId().toString())));
    if (identity.email() != null) {
      representation.put("email", identity.email());
      representation.put("emailVerified", false);
    }
    if (identity.temporaryPassword() != null) {
      representation.put("credentials", List.of(temporaryPassword(identity.temporaryPassword())));
    }

    URI location =
        call(
            "createAccount",
            null,
            () ->
                keycloakAdminRestClient
                    .post()
                    .uri("/users")
                    .body(representation)
                    .retrieve()
                    .toBodilessEntity()
                    .getHeaders()
                    .getLocation());
    UUID accountId = accountIdFrom(location);
    try {
      assignRealmRole(accountId, identity.realmRole());
    } catch (DomainException ex) {
      log.atWarn()
          .addKeyValue("accountId", accountId)
          .log("Role assignment failed; removing the half-created account");
      deleteAccount(accountId);
      throw ex;
    }
    log.atInfo()
        .addKeyValue("accountId", accountId)
        .addKeyValue("realmRole", identity.realmRole())
        .addKeyValue("credentialMode", identity.temporaryPassword() == null ? "email" : "temporary")
        .log("Keycloak account created");
    return accountId;
  }

  @Override
  public void updateProfile(UUID accountId, IdentityProfile profile) {
    Map<String, Object> representation = fetchUser(accountId);
    representation.put("firstName", profile.firstName());
    representation.put("lastName", profile.lastName());
    if (profile.email() != null && !profile.email().equals(representation.get("email"))) {
      representation.put("email", profile.email());
      representation.put("emailVerified", false);
    }
    putUser("updateProfile", accountId, representation);
    log.atInfo().addKeyValue("accountId", accountId).log("Keycloak account profile updated");
  }

  @Override
  public void setEnabled(UUID accountId, boolean enabled) {
    Map<String, Object> representation = fetchUser(accountId);
    representation.put("enabled", enabled);
    putUser("setEnabled", accountId, representation);
    log.atInfo()
        .addKeyValue("accountId", accountId)
        .addKeyValue("enabled", enabled)
        .log("Keycloak account enabled flag changed");
  }

  @Override
  public void deleteAccount(UUID accountId) {
    call(
        "deleteAccount",
        accountId,
        () ->
            keycloakAdminRestClient
                .delete()
                .uri("/users/{id}", accountId)
                .retrieve()
                .toBodilessEntity());
    log.atInfo().addKeyValue("accountId", accountId).log("Keycloak account deleted");
  }

  @Override
  public void sendPasswordSetupEmail(UUID accountId) {
    call(
        "sendPasswordSetupEmail",
        accountId,
        () ->
            keycloakAdminRestClient
                .put()
                .uri(
                    uri ->
                        uri.path("/users/{id}/execute-actions-email")
                            .queryParam("client_id", properties.passwordSetupClientId())
                            .queryParam("redirect_uri", properties.passwordSetupRedirectUri())
                            .queryParam("lifespan", properties.passwordSetupLifespan().toSeconds())
                            .build(accountId))
                .body(List.of("UPDATE_PASSWORD"))
                .retrieve()
                .toBodilessEntity());
    log.atInfo().addKeyValue("accountId", accountId).log("Password setup e-mail requested");
  }

  @Override
  public void setTemporaryPassword(UUID accountId, String password) {
    call(
        "setTemporaryPassword",
        accountId,
        () ->
            keycloakAdminRestClient
                .put()
                .uri("/users/{id}/reset-password", accountId)
                .body(temporaryPassword(password))
                .retrieve()
                .toBodilessEntity());
    log.atInfo().addKeyValue("accountId", accountId).log("Temporary password set");
  }

  private void assignRealmRole(UUID accountId, String roleName) {
    Map<String, Object> role =
        realmRoleCache.computeIfAbsent(
            roleName,
            name ->
                call(
                    "getRealmRole",
                    accountId,
                    () ->
                        keycloakAdminRestClient
                            .get()
                            .uri("/roles/{name}", name)
                            .retrieve()
                            .body(JSON_OBJECT)));
    call(
        "assignRealmRole",
        accountId,
        () ->
            keycloakAdminRestClient
                .post()
                .uri("/users/{id}/role-mappings/realm", accountId)
                .body(List.of(role))
                .retrieve()
                .toBodilessEntity());
  }

  /** Full representation, so a PUT does not drop fields (attributes) we did not set. */
  private Map<String, Object> fetchUser(UUID accountId) {
    Map<String, Object> user =
        call(
            "getAccount",
            accountId,
            () ->
                keycloakAdminRestClient
                    .get()
                    .uri("/users/{id}", accountId)
                    .retrieve()
                    .body(JSON_OBJECT));
    return new HashMap<>(user);
  }

  private void putUser(String operation, UUID accountId, Map<String, Object> representation) {
    call(
        operation,
        accountId,
        () ->
            keycloakAdminRestClient
                .put()
                .uri("/users/{id}", accountId)
                .body(representation)
                .retrieve()
                .toBodilessEntity());
  }

  private static Map<String, Object> temporaryPassword(String password) {
    return Map.of("type", "password", "value", password, "temporary", true);
  }

  private UUID accountIdFrom(URI location) {
    if (location == null) {
      throw Rejections.fail(
          log,
          ErrorCode.IDENTITY_PROVIDER_ERROR,
          "Keycloak created an account but returned no Location header",
          null);
    }
    String path = location.getPath();
    return UUID.fromString(path.substring(path.lastIndexOf('/') + 1));
  }

  /** Translates transport and HTTP failures into logged API errors. */
  private <T> T call(String operation, UUID accountId, Supplier<T> action) {
    try {
      return action.get();
    } catch (RestClientResponseException ex) {
      if (ex.getStatusCode().isSameCodeAs(HttpStatus.CONFLICT)) {
        throw Rejections.reject(
            log,
            ErrorCode.USERNAME_TAKEN,
            "Keycloak rejected the account: username or e-mail already exists",
            "operation",
            operation);
      }
      // No cause: the exception message contains the response body.
      throw Rejections.fail(
          log,
          ErrorCode.IDENTITY_PROVIDER_ERROR,
          "Keycloak Admin API returned an error",
          null,
          "operation",
          operation,
          "accountId",
          accountId,
          "httpStatus",
          ex.getStatusCode().value());
    } catch (ResourceAccessException | ClientAuthorizationException ex) {
      throw Rejections.fail(
          log,
          ErrorCode.IDENTITY_PROVIDER_UNAVAILABLE,
          "Keycloak is unreachable or rejected the service credentials",
          ex,
          "operation",
          operation,
          "accountId",
          accountId);
    }
  }
}
