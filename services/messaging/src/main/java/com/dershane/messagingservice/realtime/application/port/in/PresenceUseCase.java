package com.dershane.messagingservice.realtime.application.port.in;

import java.util.UUID;

/** Online tracking. Failures are tolerated: they only cost an extra notification. */
public interface PresenceUseCase {

  /** Marks the user online, or renews the mark (heartbeat). */
  void connected(UUID userId);

  void disconnected(UUID userId);
}
