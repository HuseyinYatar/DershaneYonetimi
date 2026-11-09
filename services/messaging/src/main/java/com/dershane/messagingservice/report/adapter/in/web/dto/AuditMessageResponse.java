package com.dershane.messagingservice.report.adapter.in.web.dto;

import com.dershane.messagingservice.conversation.domain.MessageType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Free text: masked in {@code toString}. */
public record AuditMessageResponse(
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
    List<AuditEditResponse> edits) {

  @Override
  public String toString() {
    return "AuditMessageResponse[id=" + id + ", seq=" + seq + "]";
  }
}
