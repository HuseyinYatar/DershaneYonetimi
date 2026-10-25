package com.dershane.userservice.shared.adapter.out.messaging;

import com.dershane.userservice.shared.application.event.IntegrationEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Wire format shared by all services (shared/events/envelope.schema.json).
 *
 * @param eventId unique per event; consumers use it to drop duplicate SQS deliveries
 * @param schemaVersion version of the {@code data} schema for this event type
 */
public record EventEnvelope(
    UUID eventId,
    String eventType,
    int schemaVersion,
    Instant occurredAt,
    String source,
    IntegrationEvent data) {

  public static final String SOURCE = "user";
  public static final int SCHEMA_VERSION = 1;

  @Override
  public String toString() {
    return "EventEnvelope[eventId=" + eventId + ", eventType=" + eventType + "]";
  }
}
