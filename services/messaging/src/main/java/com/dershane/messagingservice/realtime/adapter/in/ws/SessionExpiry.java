package com.dershane.messagingservice.realtime.adapter.in.ws;

import com.dershane.messagingservice.shared.application.MessagingProperties;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Closes sockets older than {@code messaging.realtime.max-session-age}. The client then asks for a
 * new ticket, which needs a valid token, so a signed-out or deactivated user cannot stay connected
 * for longer than that. The sweep does not depend on the client sending anything.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class SessionExpiry {

  private final SessionRegistry registry;
  private final MessagingProperties properties;
  private final Clock clock;

  @Scheduled(fixedDelayString = "PT1M")
  void closeExpired() {
    Instant cutoff = clock.instant().minus(properties.realtime().maxSessionAge());
    int closed = registry.closeConnectedBefore(cutoff, ChatWebSocketHandler.REAUTHENTICATE);
    if (closed > 0) {
      log.atInfo().addKeyValue("closed", closed).log("websocket.expired_closed");
    } else {
      log.atDebug().log("websocket.none_expired");
    }
  }
}
