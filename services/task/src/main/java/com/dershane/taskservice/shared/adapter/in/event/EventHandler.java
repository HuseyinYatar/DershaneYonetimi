package com.dershane.taskservice.shared.adapter.in.event;

import tools.jackson.databind.JsonNode;

/**
 * Applies one kind of integration event from another service. A new event type is a new handler
 * class; the consumer picks it up on its own.
 */
public interface EventHandler {

  /** Event name (envelope {@code eventType}) this handler accepts. */
  String eventType();

  /**
   * Applies the {@code data} part of an event. Runs in the same transaction that records the event
   * as processed; an exception rolls both back and leaves the message in the queue to be retried.
   */
  void handle(String eventType, JsonNode data);
}
