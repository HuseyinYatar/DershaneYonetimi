package com.dershane.messagingservice.realtime.application.port.out;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public interface TicketStorePort {

  void store(String ticket, UUID userId, Duration ttl);

  /** Reads and removes in one step, so a ticket cannot be used twice. */
  Optional<UUID> consume(String ticket);
}
