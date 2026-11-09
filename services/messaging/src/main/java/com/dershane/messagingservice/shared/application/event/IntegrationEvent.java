package com.dershane.messagingservice.shared.application.event;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import java.util.UUID;

/**
 * Events this service publishes to other services (through the outbox). Each record is the {@code
 * data} part of the envelope and must match its JSON schema in {@code shared/events/messaging/}.
 *
 * <p>Payloads carry identifiers only: no message text, no names (KVKK).
 */
public sealed interface IntegrationEvent permits IntegrationEvent.NewMessage {

  @JsonIgnore
  EventType type();

  /** The entity the event is about; used for tracing and ordering diagnostics. */
  @JsonIgnore
  UUID aggregateId();

  /** A message arrived for a recipient who had no open socket. One event per message. */
  record NewMessage(
      UUID conversationId, UUID messageId, UUID senderId, UUID recipientId, Instant sentAt)
      implements IntegrationEvent {

    @Override
    public EventType type() {
      return EventType.NEW_MESSAGE;
    }

    @Override
    public UUID aggregateId() {
      return messageId;
    }
  }
}
