package com.dershane.userservice.user.adapter.out.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dershane.userservice.shared.domain.DomainException;
import com.dershane.userservice.shared.domain.ErrorCode;
import com.dershane.userservice.support.TestContainers;
import com.dershane.userservice.user.application.port.out.IdentityProfile;
import com.dershane.userservice.user.application.port.out.NewIdentity;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import jakarta.ws.rs.NotFoundException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.web.client.RestClient;

/**
 * The Keycloak adapter against a real Keycloak with the project realm. Also proves the realm
 * config: the service account's permissions and the declared {@code branch_id} attribute (Keycloak
 * silently drops undeclared attributes).
 */
class KeycloakIdentityProviderIT {

  private static final String REALM = "dershane";

  private static KeycloakContainer keycloak;
  private static KeycloakIdentityProvider provider;
  private static UsersResource keycloakUsers;

  @BeforeAll
  static void setUp() {
    keycloak = TestContainers.keycloak();
    provider = providerFor(keycloak.getAuthServerUrl());
    keycloakUsers = keycloak.getKeycloakAdminClient().realm(REALM).users();
  }

  @Test
  void createsAccountWithRealmRoleBranchAndTemporaryPassword() {
    UUID branchId = UUID.randomUUID();
    String username = "it-" + UUID.randomUUID().toString().substring(0, 8);

    UUID accountId =
        provider.createAccount(
            new NewIdentity(username, null, "Ali", "Kaya", "student", branchId, "Tmp2345abcde"));

    UserRepresentation user = keycloakUsers.get(accountId.toString()).toRepresentation();
    assertThat(user.getUsername()).isEqualTo(username);
    assertThat(user.getAttributes())
        .containsEntry("branch_id", java.util.List.of(branchId.toString()));
    assertThat(user.getRequiredActions()).contains("UPDATE_PASSWORD");
    assertThat(keycloakUsers.get(accountId.toString()).roles().realmLevel().listEffective())
        .extracting(RoleRepresentation::getName)
        .contains("student");
  }

  @Test
  void duplicateUsernameIsReportedAsTaken() {
    String username = "dup-" + UUID.randomUUID().toString().substring(0, 8);
    provider.createAccount(
        new NewIdentity(
            username, null, "Ali", "Kaya", "student", UUID.randomUUID(), "Tmp2345abcde"));

    assertThatThrownBy(
            () ->
                provider.createAccount(
                    new NewIdentity(
                        username,
                        null,
                        "Veli",
                        "Kaya",
                        "student",
                        UUID.randomUUID(),
                        "Tmp2345abcde")))
        .isInstanceOf(DomainException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.USERNAME_TAKEN);
  }

  @Test
  void profileUpdateKeepsTheBranchAttribute() {
    UUID branchId = UUID.randomUUID();
    String email = "it-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    UUID accountId =
        provider.createAccount(
            new NewIdentity(email, email, "Can", "Demir", "teacher", branchId, null));

    provider.updateProfile(accountId, new IdentityProfile(email, "Can", "Yıldız"));

    UserRepresentation user = keycloakUsers.get(accountId.toString()).toRepresentation();
    assertThat(user.getLastName()).isEqualTo("Yıldız");
    assertThat(user.getAttributes())
        .containsEntry("branch_id", java.util.List.of(branchId.toString()));
  }

  @Test
  void disablesAndDeletesAccounts() {
    UUID accountId =
        provider.createAccount(
            new NewIdentity(
                "del-" + UUID.randomUUID().toString().substring(0, 8),
                null,
                "Ali",
                "Kaya",
                "guardian",
                UUID.randomUUID(),
                "Tmp2345abcde"));

    provider.setEnabled(accountId, false);
    assertThat(keycloakUsers.get(accountId.toString()).toRepresentation().isEnabled()).isFalse();

    provider.deleteAccount(accountId);
    assertThatThrownBy(() -> keycloakUsers.get(accountId.toString()).toRepresentation())
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void unreachableKeycloakIsReportedAsUnavailable() {
    KeycloakIdentityProvider unreachable = providerFor("http://localhost:1");

    assertThatThrownBy(
            () ->
                unreachable.createAccount(
                    new NewIdentity("x-user", null, "A", "B", "student", UUID.randomUUID(), "pw")))
        .isInstanceOf(DomainException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.IDENTITY_PROVIDER_UNAVAILABLE);
  }

  /** Wires the adapter exactly like {@link KeycloakAdminClientConfig} does in the application. */
  private static KeycloakIdentityProvider providerFor(String serverUrl) {
    KeycloakProperties properties =
        new KeycloakProperties(
            serverUrl,
            REALM,
            "keycloak-admin",
            "dershane-frontend",
            "http://localhost:3000",
            Duration.ofHours(72),
            Duration.ofSeconds(2),
            Duration.ofSeconds(10));
    ClientRegistration registration =
        ClientRegistration.withRegistrationId("keycloak-admin")
            .clientId("user-service")
            .clientSecret("user-service-local-secret")
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .tokenUri(serverUrl + "/realms/" + REALM + "/protocol/openid-connect/token")
            .build();
    InMemoryClientRegistrationRepository registrations =
        new InMemoryClientRegistrationRepository(registration);
    KeycloakAdminClientConfig config = new KeycloakAdminClientConfig();
    OAuth2AuthorizedClientManager manager =
        config.keycloakAdminAuthorizedClientManager(
            registrations, new InMemoryOAuth2AuthorizedClientService(registrations), properties);
    RestClient client = config.keycloakAdminRestClient(RestClient.builder(), properties, manager);
    return new KeycloakIdentityProvider(client, properties);
  }
}
