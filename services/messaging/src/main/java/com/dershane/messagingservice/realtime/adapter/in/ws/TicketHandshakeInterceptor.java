package com.dershane.messagingservice.realtime.adapter.in.ws;

import com.dershane.messagingservice.realtime.application.port.in.RedeemTicketUseCase;
import com.dershane.messagingservice.shared.domain.DomainException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

/** Authenticates the handshake with the one-time ticket from the query string. */
@Slf4j
@Component
@RequiredArgsConstructor
class TicketHandshakeInterceptor implements HandshakeInterceptor {

  static final String USER_ID = "userId";

  private final RedeemTicketUseCase redeemTicket;

  @Override
  public boolean beforeHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler handler,
      Map<String, Object> attributes) {
    String ticket =
        UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("ticket");
    if (ticket == null || ticket.isBlank()) {
      log.atWarn().addKeyValue("reason", "missing").log("websocket.ticket_rejected");
      response.setStatusCode(HttpStatus.UNAUTHORIZED);
      return false;
    }
    Optional<UUID> userId;
    try {
      userId = redeemTicket.redeem(ticket);
    } catch (DomainException e) {
      // Already logged where it was thrown.
      response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
      return false;
    }
    if (userId.isEmpty()) {
      log.atWarn().addKeyValue("reason", "unknown_or_used").log("websocket.ticket_rejected");
      response.setStatusCode(HttpStatus.UNAUTHORIZED);
      return false;
    }
    attributes.put(USER_ID, userId.get());
    return true;
  }

  @Override
  public void afterHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler handler,
      Exception exception) {
    // Nothing to do.
  }
}
