package com.dershane.messagingservice.realtime.adapter.in.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

class SessionRegistryTest {

  private static final Instant NOW = Instant.parse("2026-11-09T12:00:00Z");

  private final SessionRegistry registry = new SessionRegistry(new SimpleMeterRegistry());
  private final UUID userId = UUID.randomUUID();

  private WebSocketSession socket(String id, Instant connectedAt) {
    WebSocketSession session = mock(WebSocketSession.class);
    HashMap<String, Object> attributes = new HashMap<>();
    attributes.put(ChatWebSocketHandler.CONNECTED_AT, connectedAt);
    lenient().when(session.getId()).thenReturn(id);
    lenient().when(session.getAttributes()).thenReturn(attributes);
    return session;
  }

  @Test
  void aSilentSocketIsClosedOnceItIsOlderThanTheCutoff() throws IOException {
    WebSocketSession old = socket("old", NOW.minusSeconds(7200));
    WebSocketSession fresh = socket("fresh", NOW.minusSeconds(60));
    registry.add(userId, old);
    registry.add(userId, fresh);

    int closed =
        registry.closeConnectedBefore(NOW.minusSeconds(3600), ChatWebSocketHandler.REAUTHENTICATE);

    assertThat(closed).isEqualTo(1);
    verify(old).close(ChatWebSocketHandler.REAUTHENTICATE);
    verify(fresh, never()).close(org.mockito.ArgumentMatchers.any(CloseStatus.class));
  }

  @Test
  void theOldestSocketGoesWhenAUserOpensTooMany() throws IOException {
    WebSocketSession oldest = socket("s0", NOW.minusSeconds(1000));
    registry.add(userId, oldest);
    for (int i = 1; i <= SessionRegistry.MAX_SESSIONS_PER_USER; i++) {
      registry.add(userId, socket("s" + i, NOW.minusSeconds(1000 - i)));
    }

    verify(oldest).close(SessionRegistry.TOO_MANY_SESSIONS);
  }

  @Test
  void aUserWithinTheCapKeepsEverySocket() throws IOException {
    WebSocketSession first = socket("s0", NOW.minusSeconds(1000));
    registry.add(userId, first);
    for (int i = 1; i < SessionRegistry.MAX_SESSIONS_PER_USER; i++) {
      registry.add(userId, socket("s" + i, NOW.minusSeconds(1000 - i)));
    }

    verify(first, never()).close(org.mockito.ArgumentMatchers.any(CloseStatus.class));
  }
}
