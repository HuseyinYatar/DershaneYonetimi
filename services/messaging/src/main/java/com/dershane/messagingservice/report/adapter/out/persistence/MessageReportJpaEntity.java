package com.dershane.messagingservice.report.adapter.out.persistence;

import com.dershane.messagingservice.report.domain.ReportStatus;
import com.dershane.messagingservice.shared.adapter.out.persistence.AuditableJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "message_report")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
class MessageReportJpaEntity extends AuditableJpaEntity {

  @Id @ToString.Include private UUID id;

  @Column(name = "message_id", nullable = false, updatable = false)
  private UUID messageId;

  @Column(name = "conversation_id", nullable = false, updatable = false)
  private UUID conversationId;

  @Column(name = "branch_id", nullable = false, updatable = false)
  private UUID branchId;

  @Column(name = "reporter_id", nullable = false, updatable = false)
  private UUID reporterId;

  @Column(name = "reason", nullable = false, updatable = false)
  private String reason;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private ReportStatus status;

  @Column(name = "reviewed_by")
  private UUID reviewedBy;

  @Column(name = "reviewed_at")
  private Instant reviewedAt;

  @Column(name = "resolution_note")
  private String resolutionNote;
}
