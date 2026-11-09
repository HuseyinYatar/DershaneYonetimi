package com.dershane.messagingservice.report.adapter.out.persistence;

import com.dershane.messagingservice.report.application.port.out.ReportPort;
import com.dershane.messagingservice.report.domain.MessageReport;
import com.dershane.messagingservice.report.domain.ReportStatus;
import com.dershane.messagingservice.shared.adapter.out.persistence.VersionChecks;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class ReportPersistenceAdapter implements ReportPort {

  private final MessageReportJpaRepository reports;

  @Override
  public Optional<MessageReport> findById(UUID reportId) {
    return reports.findById(reportId).map(ReportPersistenceAdapter::toDomain);
  }

  @Override
  public boolean existsByMessageAndReporter(UUID messageId, UUID reporterId) {
    return reports.existsByMessageIdAndReporterId(messageId, reporterId);
  }

  @Override
  public Page<MessageReport> findAll(UUID branchId, ReportStatus status, Pageable pageable) {
    return reports.search(branchId, status, pageable).map(ReportPersistenceAdapter::toDomain);
  }

  @Override
  public MessageReport save(MessageReport report) {
    MessageReportJpaEntity entity;
    if (report.getVersion() == null) {
      entity = new MessageReportJpaEntity();
      entity.setId(report.getId());
      entity.setMessageId(report.getMessageId());
      entity.setConversationId(report.getConversationId());
      entity.setBranchId(report.getBranchId());
      entity.setReporterId(report.getReporterId());
      entity.setReason(report.getReason());
    } else {
      entity = reports.findById(report.getId()).orElseThrow();
      VersionChecks.requireCurrent(
          log, "MessageReport", report.getId(), report.getVersion(), entity.getVersion());
    }
    entity.setStatus(report.getStatus());
    entity.setReviewedBy(report.getReviewedBy());
    entity.setReviewedAt(report.getReviewedAt());
    entity.setResolutionNote(report.getResolutionNote());
    return toDomain(reports.saveAndFlush(entity));
  }

  private static MessageReport toDomain(MessageReportJpaEntity e) {
    return new MessageReport(
        e.getId(),
        e.getVersion(),
        e.getMessageId(),
        e.getConversationId(),
        e.getBranchId(),
        e.getReporterId(),
        e.getReason(),
        e.getStatus(),
        e.getCreatedAt(),
        e.getReviewedBy(),
        e.getReviewedAt(),
        e.getResolutionNote());
  }
}
