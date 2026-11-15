package com.dershane.messagingservice.realtime.adapter.in.ws;

import com.dershane.messagingservice.shared.application.MetricNames;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
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

  /** shortcut: fixed cap; move it to MessagingProperties when a deployment needs another one. */
  static final int MAX_SESSIONS_PER_USER = 5;

  static final CloseStatus TOO_MANY_SESSIONS = new CloseStatus(4429, "too many connections");

  void add(UUID userId, WebSocketSession session) {
    Map<String, WebSocketSession> userSessions =
        sessions.computeIfAbsent(userId, k -> new ConcurrentHashMap<>());
    userSessions.put(session.getId(), session);
    count.incrementAndGet();
    closeOldestBeyondCap(userId, userSessions);
  }

  /** One user cannot hold the pod's sockets and send buffers by opening connections in a loop. */
  private void closeOldestBeyondCap(UUID userId, Map<String, WebSocketSession> userSessions) {
    while (userSessions.size() > MAX_SESSIONS_PER_USER) {
      WebSocketSession oldest =
          userSessions.values().stream()
              .min(Comparator.comparing(SessionRegistry::connectedAt))
              .orElseThrow();
      userSessions.remove(oldest.getId());
      count.decrementAndGet();
      log.atWarn()
          .addKeyValue("userId", userId)
          .addKeyValue("sessionId", oldest.getId())
          .log("websocket.closed_over_cap");
      close(oldest, TOO_MANY_SESSIONS);
    }
  }

  /**
   * Closes every socket connected before {@code cutoff}. The age limit is enforced here and not
   * only when the client sends a frame, so a socket that stays silent cannot outlive it.
   *
   * @return how many sockets were closed
   */
  int closeConnectedBefore(Instant cutoff, CloseStatus status) {
    int closed = 0;
    for (Map<String, WebSocketSession> userSessions : sessions.values()) {
      for (WebSocketSession session : List.copyOf(userSessions.values())) {
        if (connectedAt(session).isBefore(cutoff)) {
          close(session, status);
          closed++;
        }
      }
    }
    return closed;
  }

  private static Instant connectedAt(WebSocketSession session) {
    Object connectedAt = session.getAttributes().get(ChatWebSocketHandler.CONNECTED_AT);
    return connectedAt instanceof Instant instant ? instant : Instant.EPOCH;
  }

  private static void close(WebSocketSession session, CloseStatus status) {
    try {
      session.close(status);
    } catch (IOException | RuntimeException e) {
      log.atWarn()
          .addKeyValue("sessionId", session.getId())
          .addKeyValue("exception", e.getClass().getSimpleName())
          .log("websocket.close_failed");
    }
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
