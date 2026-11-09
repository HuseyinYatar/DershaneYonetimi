package com.dershane.messagingservice.report.application.port.in;

import java.util.UUID;

/**
 * Personal data (name): masked in {@code toString}. Names are null when the user copy is missing.
 */
public record PartyView(UUID id, String firstName, String lastName) {

  @Override
  public String toString() {
    return "PartyView[id=" + id + "]";
  }
}
