package com.dershane.messagingservice.conversation.application.port.in;

import com.dershane.messagingservice.conversation.domain.MessageType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A message with everything an administrator reviewing a report may see: the content of a deleted
 * message and the earlier texts of an edited one. Free text: masked in {@code toString}.
 */
public record AuditMessageView(
    UUID id,
    long seq,
    UUID senderId,
    MessageType type,
    String content,
    UUID fileId,
    Instant sentAt,
    Instant editedAt,
    boolean deleted,
    Instant deletedAt,
    List<AuditEditView> edits) {

  @Override
  public String toString() {
    return "AuditMessageView[id=" + id + ", seq=" + seq + "]";
  }
}
