package com.dershane.messagingservice.realtime.application.service;

import com.dershane.messagingservice.realtime.application.port.in.IssueTicketUseCase;
import com.dershane.messagingservice.realtime.application.port.in.RedeemTicketUseCase;
import com.dershane.messagingservice.realtime.application.port.in.TicketView;
import com.dershane.messagingservice.realtime.application.port.out.TicketStorePort;
import com.dershane.messagingservice.shared.application.Actor;
import com.dershane.messagingservice.shared.application.MessagingProperties;
import com.dershane.messagingservice.shared.domain.ErrorCode;
import com.dershane.messagingservice.shared.domain.Rejections;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
class TicketService implements IssueTicketUseCase, RedeemTicketUseCase {

  private static final int TICKET_BYTES = 32;

  private final TicketStorePort tickets;
  private final MessagingProperties properties;
  private final SecureRandom random = new SecureRandom();

  @Override
  public TicketView issue(Actor actor) {
    byte[] bytes = new byte[TICKET_BYTES];
    random.nextBytes(bytes);
    String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    Duration ttl = properties.realtime().ticketTtl();
    try {
      tickets.store(ticket, actor.id(), ttl);
    } catch (RuntimeException e) {
      throw Rejections.fail(
          log,
          ErrorCode.REALTIME_UNAVAILABLE,
          "Could not store the WebSocket ticket",
          e,
          "callerId",
          actor.id());
    }
    return new TicketView(ticket, ttl.toSeconds());
  }

  @Override
  public Optional<UUID> redeem(String ticket) {
    try {
      return tickets.consume(ticket);
    } catch (RuntimeException e) {
      throw Rejections.fail(
          log, ErrorCode.REALTIME_UNAVAILABLE, "Could not read the WebSocket ticket", e);
    }
  }
}
