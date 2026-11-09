package com.dershane.messagingservice.realtime.application.port.in;

import com.dershane.messagingservice.shared.application.Actor;

public interface IssueTicketUseCase {

  /**
   * A one-time ticket for the WebSocket handshake; browsers cannot send an Authorization header.
   */
  TicketView issue(Actor actor);
}
