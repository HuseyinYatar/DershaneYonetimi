package com.dershane.userservice.user.adapter.out.keycloak;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param serverUrl Keycloak base URL reachable from this service (internal hostname in a cluster)
 * @param realm realm of the application users
 * @param clientRegistrationId Spring OAuth2 client registration used for the Admin API
 *     (client-credentials of the {@code user-service} client)
 * @param passwordSetupClientId client the password setup e-mail redirects to after completion
 * @param passwordSetupRedirectUri where the user lands after setting the password
 * @param passwordSetupLifespan validity of the password setup link
 */
@Validated
@ConfigurationProperties("user.keycloak")
public record KeycloakProperties(
    @NotBlank String serverUrl,
    @NotBlank String realm,
    @NotBlank String clientRegistrationId,
    @NotBlank String passwordSetupClientId,
    @NotBlank String passwordSetupRedirectUri,
    @NotNull Duration passwordSetupLifespan,
    @NotNull Duration connectTimeout,
    @NotNull Duration readTimeout) {}
