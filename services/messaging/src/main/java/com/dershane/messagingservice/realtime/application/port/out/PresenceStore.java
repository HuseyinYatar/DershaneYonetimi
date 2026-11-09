package com.dershane.messagingservice.realtime.application.port.out;

import java.time.Duration;
import java.util.UUID;

public interface PresenceStore {

  void markOnline(UUID userId, Duration ttl);

  void clear(UUID userId);
}
