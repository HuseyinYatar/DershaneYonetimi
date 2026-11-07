package com.dershane.taskservice.shared.adapter.in.event;

import java.util.Set;
import tools.jackson.databind.JsonNode;

/** Applies integration events from other services; one handler per event type. */
public interface EventHandler {

  /** Event names (envelope {@code eventType}) this handler accepts. */
  Set<String> eventTypes();

  /**
   * Applies the {@code data} part of an event. Runs in the same transaction that records the event
   * as processed; an exception rolls both back and leaves the message in the queue to be retried.
   */
  void handle(String eventType, JsonNode data);
}
