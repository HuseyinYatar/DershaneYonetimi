package com.dershane.messagingservice.shared.application.port.out;

import java.util.UUID;

/** Remembers which events from other services were already applied (at-least-once delivery). */
public interface ProcessedEventPort {

  /**
   * Records the event in the caller's transaction.
   *
   * @return false when it was recorded before, i.e. this delivery is a duplicate
   */
  boolean markProcessed(UUID eventId, String eventType);
}
