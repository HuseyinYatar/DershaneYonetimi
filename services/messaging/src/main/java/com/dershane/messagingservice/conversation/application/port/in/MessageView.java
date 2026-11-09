package com.dershane.messagingservice.conversation.application.port.in;

import com.dershane.messagingservice.conversation.domain.MessageType;
import java.time.Instant;
import java.util.UUID;

/**
 * A message as clients see it. A deleted message has no {@code content} and no {@code fileId}. Free
 * text: masked in {@code toString}.
 */
public record MessageView(
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
    return "MessageView[id=" + id + ", conversationId=" + conversationId + ", seq=" + seq + "]";
  }
}
