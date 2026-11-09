package com.dershane.messagingservice.conversation.adapter.in.web.dto;

import com.dershane.messagingservice.conversation.domain.MessageType;
import java.time.Instant;
import java.util.UUID;

/** Free text: masked in {@code toString}. */
public record MessageResponse(
    UUID id,
    UUID conversationId,
    long seq,
    UUID senderId,
    MessageType type,
    String content,
    UUID fileId,
    Instant sentAt,
    Instant editedAt,
    boolean deleted,
    Instant updatedAt) {

  @Override
  public String toString() {
    return "MessageResponse[id=" + id + ", seq=" + seq + "]";
  }
}
