package com.dershane.messagingservice.conversation.application.port.out;

import java.util.UUID;

/** Whether a user has an open socket. Only decides whether a notification event is needed. */
public interface PresencePort {

  /** False when unknown (the store is unreachable): an extra notification beats a missed one. */
  boolean isOnline(UUID userId);
}
