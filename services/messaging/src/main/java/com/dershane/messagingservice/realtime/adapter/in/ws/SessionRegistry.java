package com.dershane.messagingservice.realtime.adapter.in.ws;

import com.dershane.messagingservice.shared.application.MetricNames;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/** The sockets open on this pod, by user. */
@Slf4j
@Component
public class SessionRegistry {

  private final Map<UUID, Map<String, WebSocketSession>> sessions = new ConcurrentHashMap<>();
  private final AtomicInteger count = new AtomicInteger();

  public SessionRegistry(MeterRegistry meters) {
    meters.gauge(MetricNames.WEBSOCKET_SESSIONS, count);
  }

  void add(UUID userId, WebSocketSession session) {
    sessions.computeIfAbsent(userId, k -> new ConcurrentHashMap<>()).put(session.getId(), session);
    count.incrementAndGet();
  }

  /**
   * @return true when the user has no socket left on this pod
   */
  boolean remove(UUID userId, String sessionId) {
    boolean[] last = {false};
    sessions.computeIfPresent(
        userId,
        (id, userSessions) -> {
          if (userSessions.remove(sessionId) != null) {
            count.decrementAndGet();
          }
          last[0] = userSessions.isEmpty();
          return last[0] ? null : userSessions;
        });
    return last[0];
  }

  /** Sends a frame to every socket of the given users on this pod. */
  public void send(Collection<UUID> userIds, String frame) {
    TextMessage message = new TextMessage(frame);
    for (UUID userId : userIds) {
      Map<String, WebSocketSession> userSessions = sessions.get(userId);
      if (userSessions == null) {
        continue;
      }
      for (WebSocketSession session : userSessions.values()) {
        try {
          session.sendMessage(message);
        } catch (IOException | RuntimeException e) {
          // A slow or broken socket is dropped by the decorator; the client re-syncs on reconnect.
          log.atWarn()
              .addKeyValue("userId", userId)
              .addKeyValue("sessionId", session.getId())
              .setCause(e)
              .log("websocket.send_failed");
        }
      }
    }
  }
}
