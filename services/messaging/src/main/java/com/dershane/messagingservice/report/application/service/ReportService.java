package com.dershane.messagingservice.report.application.service;

import com.dershane.messagingservice.conversation.application.port.in.AuditMessageQuery;
import com.dershane.messagingservice.conversation.application.port.in.AuditMessageView;
import com.dershane.messagingservice.conversation.application.port.in.ReportableMessage;
import com.dershane.messagingservice.conversation.application.port.in.ReportableMessageQuery;
import com.dershane.messagingservice.directory.application.port.in.UserDirectoryQuery;
import com.dershane.messagingservice.report.application.port.in.CloseReportUseCase;
import com.dershane.messagingservice.report.application.port.in.FileReportCommand;
import com.dershane.messagingservice.report.application.port.in.FileReportUseCase;
import com.dershane.messagingservice.report.application.port.in.PartyView;
import com.dershane.messagingservice.report.application.port.in.ReportQuery;
import com.dershane.messagingservice.report.application.port.in.ReportView;
import com.dershane.messagingservice.report.application.port.out.ReportPort;
import com.dershane.messagingservice.report.domain.MessageReport;
import com.dershane.messagingservice.report.domain.ReportStatus;
import com.dershane.messagingservice.shared.application.Actor;
import com.dershane.messagingservice.shared.application.BranchAccessPolicy;
import com.dershane.messagingservice.shared.application.MetricNames;
import com.dershane.messagingservice.shared.domain.ErrorCode;
import com.dershane.messagingservice.shared.domain.Rejections;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.annotation.Observed;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
class ReportService implements FileReportUseCase, CloseReportUseCase, ReportQuery {

  private final ReportPort reports;
  private final ReportableMessageQuery reportableMessages;
  private final AuditMessageQuery auditMessages;
  private final UserDirectoryQuery directory;
  private final BranchAccessPolicy branchAccess;
  private final MeterRegistry meters;
  private final Clock clock;

  @Override
  @Transactional
  @Observed(name = "report.file")
  public ReportView file(Actor actor, FileReportCommand command) {
    // Also checks that the caller takes part in the conversation of the message.
    ReportableMessage message = reportableMessages.findForReport(actor, command.messageId());
    if (reports.existsByMessageAndReporter(message.messageId(), actor.id())) {
      throw Rejections.reject(
          log,
          ErrorCode.REPORT_ALREADY_EXISTS,
          "Caller already reported this message",
          "messageId",
          message.messageId(),
          "reporterId",
          actor.id());
    }
    MessageReport saved =
        reports.save(
            MessageReport.file(
                message.messageId(),
                message.conversationId(),
                message.branchId(),
                message.senderId(),
                actor.id(),
                command.reason(),
                clock.instant()));
    meters.counter(MetricNames.REPORTS_CREATED).increment();
    log.atInfo()
        .addKeyValue("reportId", saved.getId())
        .addKeyValue("messageId", saved.getMessageId())
        .addKeyValue("reporterId", actor.id())
        .log("Message reported");
    return toView(saved, false);
  }

  @Override
  @Transactional
  @Observed(name = "report.close")
  public ReportView close(Actor actor, UUID reportId, String resolutionNote) {
    MessageReport report = load(actor, reportId, "report.close");
    report.close(actor.id(), resolutionNote, clock.instant());
    MessageReport saved = reports.save(report);
    log.atInfo()
        .addKeyValue("reportId", reportId)
        .addKeyValue("reviewerId", actor.id())
        .log("Report closed");
    return toView(saved, true);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<ReportView> list(Actor actor, ReportStatus status, Pageable pageable) {
    UUID branchId = branchAccess.resolveBranchFilter(actor, null, "report.list");
    return reports.findAll(branchId, status, pageable).map(r -> toView(r, false));
  }

  @Override
  @Transactional(readOnly = true)
  public ReportView get(Actor actor, UUID reportId) {
    return toView(load(actor, reportId, "report.get"), true);
  }

  @Override
  @Transactional(readOnly = true)
  public List<AuditMessageView> auditMessages(
      Actor actor, UUID reportId, Long beforeSeq, int limit) {
    MessageReport report = load(actor, reportId, "report.audit");
    log.atInfo()
        .addKeyValue("event", "conversation.audit_read")
        .addKeyValue("actorId", actor.id())
        .addKeyValue("reportId", reportId)
        .addKeyValue("conversationId", report.getConversationId())
        .log("conversation.audit_read");
    return auditMessages.auditMessages(report.getConversationId(), beforeSeq, limit);
  }

  private MessageReport load(Actor actor, UUID reportId, String action) {
    MessageReport report =
        reports
            .findById(reportId)
            .orElseThrow(
                () ->
                    Rejections.reject(
                        log,
                        ErrorCode.REPORT_NOT_FOUND,
                        "Report does not exist",
                        "reportId",
                        reportId));
    branchAccess.requireBranchAdmin(actor, report.getBranchId(), action);
    return report;
  }

  private ReportView toView(MessageReport r, boolean withMessage) {
    AuditMessageView message =
        withMessage ? auditMessages.auditMessage(r.getMessageId()).orElse(null) : null;
    UUID senderId = message == null ? null : message.senderId();
    return new ReportView(
        r.getId(),
        r.getStatus(),
        r.getMessageId(),
        r.getConversationId(),
        r.getBranchId(),
        party(r.getReporterId()),
        senderId == null ? null : party(senderId),
        r.getReason(),
        r.getCreatedAt(),
        r.getReviewedBy(),
        r.getReviewedAt(),
        r.getResolutionNote(),
        message);
  }

  private PartyView party(UUID userId) {
    return directory
        .find(userId)
        .map(e -> new PartyView(e.userId(), e.firstName(), e.lastName()))
        .orElse(new PartyView(userId, null, null));
  }
}
