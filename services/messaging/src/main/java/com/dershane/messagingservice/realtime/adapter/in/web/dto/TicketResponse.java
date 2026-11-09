package com.dershane.messagingservice.realtime.adapter.in.web.dto;

public record TicketResponse(String ticket, long expiresInSeconds) {

  @Override
  public String toString() {
    return "TicketResponse[expiresInSeconds=" + expiresInSeconds + "]";
  }
}
