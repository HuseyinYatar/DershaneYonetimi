package com.dershane.messagingservice.report.adapter.in.web.dto;

import java.util.UUID;

/** Personal data (name): masked in {@code toString}. */
public record PartyResponse(UUID id, String firstName, String lastName) {

  @Override
  public String toString() {
    return "PartyResponse[id=" + id + "]";
  }
}
