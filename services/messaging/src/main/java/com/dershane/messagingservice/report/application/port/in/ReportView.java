package com.dershane.messagingservice.report.application.port.in;

import com.dershane.messagingservice.conversation.application.port.in.AuditMessageView;
import com.dershane.messagingservice.report.domain.ReportStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * A report with who is involved. {@code message} (with its audit detail) is filled only when one
 * report is read, not in lists. Free text: masked in {@code toString}.
 */
public record ReportView(
    UUID id,
    ReportStatus status,
    UUID messageId,
    UUID conversationId,
    UUID branchId,
    PartyView reporter,
    PartyView sender,
    String reason,
    Instant createdAt,
    UUID reviewedBy,
    Instant reviewedAt,
    String resolutionNote,
    AuditMessageView message) {

  @Override
  public String toString() {
    return "ReportView[id=" + id + ", status=" + status + "]";
  }
}
