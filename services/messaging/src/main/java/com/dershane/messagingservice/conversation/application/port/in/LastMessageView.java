package com.dershane.messagingservice.conversation.application.port.in;

import com.dershane.messagingservice.conversation.domain.MessageType;
import java.time.Instant;
import java.util.UUID;

/** Free text preview: masked in {@code toString}. {@code preview} is null for a deleted message. */
public record LastMessageView(
    long seq, UUID senderId, MessageType type, String preview, boolean deleted, Instant sentAt) {

  @Override
  public String toString() {
    return "LastMessageView[seq=" + seq + "]";
  }
}
