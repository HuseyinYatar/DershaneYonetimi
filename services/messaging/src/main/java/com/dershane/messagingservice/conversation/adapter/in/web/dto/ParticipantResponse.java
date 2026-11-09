package com.dershane.messagingservice.conversation.adapter.in.web.dto;

import com.dershane.messagingservice.directory.application.port.in.DirectoryRole;
import java.util.UUID;

/** Personal data (name): masked in {@code toString}. */
public record ParticipantResponse(
    UUID id, String firstName, String lastName, DirectoryRole role, boolean active) {

  @Override
  public String toString() {
    return "ParticipantResponse[id=" + id + "]";
  }
}
