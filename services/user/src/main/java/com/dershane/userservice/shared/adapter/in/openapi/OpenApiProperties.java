package com.dershane.userservice.shared.adapter.in.openapi;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Browser-facing Keycloak endpoints used by Swagger UI's "Authorize" button. */
@Validated
@ConfigurationProperties("user.openapi")
public record OpenApiProperties(@NotBlank String authorizationUrl, @NotBlank String tokenUrl) {}
