package com.dershane.messagingservice.report.adapter.in.web.dto;

import com.dershane.messagingservice.report.domain.ReportStatus;
import java.time.Instant;
import java.util.UUID;

/** Free text: masked in {@code toString}. */
public record ReportResponse(
    UUID id,
    ReportStatus status,
    UUID messageId,
    UUID conversationId,
    UUID branchId,
    PartyResponse reporter,
    PartyResponse sender,
    String reason,
    Instant createdAt,
    UUID reviewedBy,
    Instant reviewedAt,
    String resolutionNote,
    AuditMessageResponse message) {

  @Override
  public String toString() {
    return "ReportResponse[id=" + id + ", status=" + status + "]";
  }
}
