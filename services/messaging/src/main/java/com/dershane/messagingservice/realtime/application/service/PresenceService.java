package com.dershane.messagingservice.realtime.application.service;

import com.dershane.messagingservice.realtime.application.port.in.PresenceUseCase;
import com.dershane.messagingservice.realtime.application.port.out.PresenceStore;
import com.dershane.messagingservice.shared.application.MessagingProperties;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
class PresenceService implements PresenceUseCase {

  private final PresenceStore store;
  private final MessagingProperties properties;

  @Override
  public void connected(UUID userId) {
    try {
      store.markOnline(userId, properties.realtime().presenceTtl());
    } catch (RuntimeException e) {
      // An unknown presence is treated as offline, which only causes an extra notification.
      log.atWarn().addKeyValue("userId", userId).setCause(e).log("presence.update_failed");
    }
  }

  @Override
  public void disconnected(UUID userId) {
    try {
      store.clear(userId);
    } catch (RuntimeException e) {
      log.atWarn().addKeyValue("userId", userId).setCause(e).log("presence.clear_failed");
    }
  }
}
