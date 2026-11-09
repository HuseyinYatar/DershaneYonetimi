package com.dershane.messagingservice.realtime.adapter.in.web;

import com.dershane.messagingservice.realtime.adapter.in.web.dto.TicketResponse;
import com.dershane.messagingservice.realtime.application.port.in.IssueTicketUseCase;
import com.dershane.messagingservice.realtime.application.port.in.TicketView;
import com.dershane.messagingservice.shared.adapter.in.security.SecurityRoles;
import com.dershane.messagingservice.shared.adapter.in.web.CurrentActor;
import com.dershane.messagingservice.shared.application.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Chat")
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
class TicketController {

  private final IssueTicketUseCase issueTicket;

  @Operation(summary = "Get a one-time ticket to open the chat WebSocket")
  @PostMapping("/tickets")
  @PreAuthorize(SecurityRoles.STUDENT_OR_TEACHER)
  public TicketResponse issue(@CurrentActor Actor actor) {
    TicketView ticket = issueTicket.issue(actor);
    return new TicketResponse(ticket.ticket(), ticket.expiresInSeconds());
  }
}
