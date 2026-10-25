package com.dershane.userservice.shared.adapter.out.messaging;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param enabled whether this instance publishes outbox rows to SNS
 * @param topicArn SNS topic for this service's events ({@code user-events})
 * @param publishInterval delay between publish runs
 * @param batchSize rows locked and published per run; small, because rows stay locked while SNS is
 *     called
 * @param retention how long published rows (which contain personal data) are kept
 */
@Validated
@ConfigurationProperties("user.outbox")
public record OutboxProperties(
    boolean enabled,
    String topicArn,
    @NotNull Duration publishInterval,
    @Min(1) int batchSize,
    @NotNull Duration retention) {}
