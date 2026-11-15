package com.dershane.messagingservice.realtime.adapter.in.ws;

import com.dershane.messagingservice.realtime.application.port.in.PresenceUseCase;
import com.dershane.messagingservice.shared.application.MessagingProperties;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Server-to-client push. The only frame a client sends is {"type":"PING"} every ~30 s. */
@Slf4j
@Component
@RequiredArgsConstructor
class ChatWebSocketHandler extends TextWebSocketHandler {

  private static final int SEND_TIME_LIMIT_MS = 5000;
  private static final int SEND_BUFFER_LIMIT_BYTES = 512 * 1024;
  static final CloseStatus REAUTHENTICATE = new CloseStatus(4401, "reauthenticate");
  static final String CONNECTED_AT = "connectedAt";
  private static final String DECORATED = "decorated";

  private final SessionRegistry registry;
  private final PresenceUseCase presence;
  private final MessagingProperties properties;
  private final JsonMapper json;
  private final Clock clock;

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    UUID userId = userId(session);
    WebSocketSession decorated =
        new ConcurrentWebSocketSessionDecorator(
            session, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT_BYTES);
    session.getAttributes().put(DECORATED, decorated);
    session.getAttributes().put(CONNECTED_AT, clock.instant());
    registry.add(userId, decorated);
    presence.connected(userId);
    log.atInfo().addKeyValue("userId", userId).log("websocket.connected");
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message)
      throws IOException {
    UUID userId = userId(session);
    Instant connectedAt = (Instant) session.getAttributes().get(CONNECTED_AT);
    if (clock.instant().isAfter(connectedAt.plus(properties.realtime().maxSessionAge()))) {
      // Forces a new ticket, so a signed-out or deactivated user cannot stay connected for long.
      session.close(REAUTHENTICATE);
      return;
    }
    if (!isPing(message.getPayload())) {
      log.atWarn().addKeyValue("userId", userId).log("websocket.unknown_frame");
      return;
    }
    presence.connected(userId);
    ((WebSocketSession) session.getAttributes().get(DECORATED))
        .sendMessage(new TextMessage("{\"type\":\"PONG\"}"));
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    UUID userId = userId(session);
    if (registry.remove(userId, session.getId())) {
      presence.disconnected(userId);
    }
    log.atInfo()
        .addKeyValue("userId", userId)
        .addKeyValue("closeCode", status.getCode())
        .log("websocket.disconnected");
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable exception) {
    log.atWarn()
        .addKeyValue("userId", userId(session))
        .setCause(exception)
        .log("websocket.transport_error");
  }

  private boolean isPing(String payload) {
    try {
      JsonNode node = json.readTree(payload);
      return "PING".equals(node.path("type").asString(null));
    } catch (RuntimeException e) {
      return false;
    }
  }

  private static UUID userId(WebSocketSession session) {
    return (UUID) session.getAttributes().get(TicketHandshakeInterceptor.USER_ID);
  }
}
