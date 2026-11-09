package com.dershane.messagingservice.realtime.adapter.in.ws;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Configuration(proxyBeanMethods = false)
@EnableWebSocket
@RequiredArgsConstructor
class WebSocketConfig implements WebSocketConfigurer {

  private final ChatWebSocketHandler handler;
  private final TicketHandshakeInterceptor ticketInterceptor;

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry
        .addHandler(handler, "/api/v1/chat/ws")
        .addInterceptors(ticketInterceptor)
        // Any origin: authentication is a one-time ticket, not a cookie, so a foreign page cannot
        // hijack a user's socket. CORS itself is handled by the gateway.
        .setAllowedOriginPatterns("*");
  }
}
