package com.dershane.messagingservice.shared.application;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Messaging rules that deployments may tune ({@code messaging.*} in application.yml). It lives in
 * {@code shared} because several modules read it and {@code shared} must not depend on a business
 * module.
 *
 * @param message message rules
 * @param realtime WebSocket and presence settings
 */
@Validated
@ConfigurationProperties("messaging")
public record MessagingProperties(@DefaultValue Message message, @DefaultValue Realtime realtime) {

  /**
   * @param editWindow the sender may edit a message this long after sending it
   */
  public record Message(@DefaultValue("15m") Duration editWindow) {}

  /**
   * @param ticketTtl how long a WebSocket ticket is valid
   * @param presenceTtl a user counts as online this long after the last heartbeat
   * @param maxSessionAge a socket is closed after this long so the client re-authenticates
   * @param channel Redis Pub/Sub channel that carries frames between pods
   */
  public record Realtime(
      @DefaultValue("30s") Duration ticketTtl,
      @DefaultValue("70s") Duration presenceTtl,
      @DefaultValue("60m") Duration maxSessionAge,
      @DefaultValue("messaging:frames") @NotBlank String channel) {}
}
