package com.dershane.userservice.shared.adapter.in.web.idempotency;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param retention how long a key is remembered; stored responses are deleted afterwards
 */
@Validated
@ConfigurationProperties("user.idempotency")
public record IdempotencyProperties(@NotNull Duration retention) {}
