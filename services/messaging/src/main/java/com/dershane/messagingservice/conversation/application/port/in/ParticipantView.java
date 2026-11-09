package com.dershane.messagingservice.conversation.application.port.in;

import com.dershane.messagingservice.directory.application.port.in.DirectoryRole;
import java.util.UUID;

/**
 * The other side of a conversation. Names are null when the user copy is missing. Personal data:
 * masked in {@code toString}.
 */
public record ParticipantView(
    UUID id, String firstName, String lastName, DirectoryRole role, boolean active) {

  @Override
  public String toString() {
    return "ParticipantView[id=" + id + "]";
  }
}
