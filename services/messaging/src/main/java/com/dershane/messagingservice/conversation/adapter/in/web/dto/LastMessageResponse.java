package com.dershane.messagingservice.conversation.adapter.in.web.dto;

import com.dershane.messagingservice.conversation.domain.MessageType;
import java.time.Instant;
import java.util.UUID;

/** Free text preview: masked in {@code toString}. */
public record LastMessageResponse(
    long seq, UUID senderId, MessageType type, String preview, boolean deleted, Instant sentAt) {

  @Override
  public String toString() {
    return "LastMessageResponse[seq=" + seq + "]";
  }
}
