package com.dershane.attendanceservice.shared.adapter.in.event;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param enabled whether this instance reads events from SQS (off for scheduled jobs)
 * @param queueName queue subscribed to the topics of the services this one listens to; defined in
 *     infra/terraform
 * @param waitTime how long one receive call waits for a message (SQS allows at most 20 seconds)
 * @param batchSize messages per receive call (SQS allows at most 10)
 * @param errorBackoff pause after a failed receive, e.g. while SQS is unreachable
 */
@Validated
@ConfigurationProperties("attendance.inbox")
public record InboxProperties(
    boolean enabled,
    @NotBlank String queueName,
    @NotNull Duration waitTime,
    @Min(1) @Max(10) int batchSize,
    @NotNull Duration errorBackoff) {}
