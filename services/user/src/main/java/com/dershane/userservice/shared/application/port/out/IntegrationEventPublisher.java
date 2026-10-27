package com.dershane.userservice.shared.application.port.out;

import com.dershane.userservice.shared.application.event.IntegrationEvent;
import java.util.UUID;

/**
 * Hands an event to the transactional outbox. It must be called inside the transaction of the
 * business change, so the event exists if and only if the change was committed.
 */
public interface IntegrationEventPublisher {

  /** Returns the id of the recorded event. */
  UUID publish(IntegrationEvent event);
}
