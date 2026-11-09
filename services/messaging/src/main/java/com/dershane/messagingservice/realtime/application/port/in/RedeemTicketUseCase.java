package com.dershane.messagingservice.realtime.application.port.in;

import java.util.Optional;
import java.util.UUID;

public interface RedeemTicketUseCase {

  /** The user the ticket was issued to; a ticket works once. Empty if unknown or expired. */
  Optional<UUID> redeem(String ticket);
}
