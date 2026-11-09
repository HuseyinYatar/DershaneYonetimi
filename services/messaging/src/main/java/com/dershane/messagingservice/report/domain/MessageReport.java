package com.dershane.messagingservice.report.domain;

import com.dershane.messagingservice.shared.domain.ErrorCode;
import com.dershane.messagingservice.shared.domain.Rejections;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/** A participant's complaint about one message. */
@Slf4j
@Getter
public class MessageReport {

  private final UUID id;

  /** Version the report was loaded with; null until saved. */
  private final Long version;

  private final UUID messageId;
  private final UUID conversationId;
  private final UUID branchId;
  private final UUID reporterId;
  private final Instant createdAt;

  // Free text: never logged, masked in toString.
  private final String reason;

  private ReportStatus status;
  private UUID reviewedBy;
  private Instant reviewedAt;
  private String resolutionNote;

  @SuppressWarnings("checkstyle:ParameterNumber")
  public MessageReport(
      UUID id,
      Long version,
      UUID messageId,
      UUID conversationId,
      UUID branchId,
      UUID reporterId,
      String reason,
      ReportStatus status,
      Instant createdAt,
      UUID reviewedBy,
      Instant reviewedAt,
      String resolutionNote) {
    this.id = id;
    this.version = version;
    this.messageId = messageId;
    this.conversationId = conversationId;
    this.branchId = branchId;
    this.reporterId = reporterId;
    this.reason = reason;
    this.status = status;
    this.createdAt = createdAt;
    this.reviewedBy = reviewedBy;
    this.reviewedAt = reviewedAt;
    this.resolutionNote = resolutionNote;
  }

  public static MessageReport file(
      UUID messageId,
      UUID conversationId,
      UUID branchId,
      UUID senderId,
      UUID reporterId,
      String reason,
      Instant now) {
    if (senderId.equals(reporterId)) {
      throw Rejections.reject(
          log,
          ErrorCode.OWN_MESSAGE_REPORT,
          "A participant cannot report their own message",
          "messageId",
          messageId,
          "reporterId",
          reporterId);
    }
    return new MessageReport(
        UUID.randomUUID(),
        null,
        messageId,
        conversationId,
        branchId,
        reporterId,
        reason,
        ReportStatus.OPEN,
        now,
        null,
        null,
        null);
  }

  public void close(UUID reviewerId, String note, Instant now) {
    if (status == ReportStatus.CLOSED) {
      throw Rejections.reject(
          log, ErrorCode.REPORT_ALREADY_CLOSED, "Report is already closed", "reportId", id);
    }
    status = ReportStatus.CLOSED;
    reviewedBy = reviewerId;
    reviewedAt = now;
    resolutionNote = note == null || note.isBlank() ? null : note;
  }

  @Override
  public String toString() {
    return "MessageReport[id=" + id + ", status=" + status + "]";
  }
}
