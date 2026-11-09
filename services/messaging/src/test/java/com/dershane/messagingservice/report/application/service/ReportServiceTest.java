package com.dershane.messagingservice.report.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dershane.messagingservice.conversation.application.port.in.AuditMessageQuery;
import com.dershane.messagingservice.conversation.application.port.in.ReportableMessage;
import com.dershane.messagingservice.conversation.application.port.in.ReportableMessageQuery;
import com.dershane.messagingservice.directory.application.port.in.UserDirectoryQuery;
import com.dershane.messagingservice.report.application.port.in.FileReportCommand;
import com.dershane.messagingservice.report.application.port.out.ReportPort;
import com.dershane.messagingservice.report.domain.MessageReport;
import com.dershane.messagingservice.report.domain.ReportStatus;
import com.dershane.messagingservice.shared.application.Actor;
import com.dershane.messagingservice.shared.application.BranchAccessPolicy;
import com.dershane.messagingservice.shared.domain.DomainException;
import com.dershane.messagingservice.shared.domain.ErrorCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-09T08:00:00Z");

  @Mock private ReportPort reports;
  @Mock private ReportableMessageQuery reportableMessages;
  @Mock private AuditMessageQuery auditMessages;
  @Mock private UserDirectoryQuery directory;

  private final UUID branchId = UUID.randomUUID();
  private final UUID studentId = UUID.randomUUID();
  private final UUID teacherId = UUID.randomUUID();
  private final Actor student = new Actor(studentId, Set.of(Actor.STUDENT), branchId);
  private final Actor admin = new Actor(UUID.randomUUID(), Set.of(Actor.ADMIN), branchId);
  private final Actor otherBranchAdmin =
      new Actor(UUID.randomUUID(), Set.of(Actor.ADMIN), UUID.randomUUID());
  private final Actor teacherActor = new Actor(teacherId, Set.of(Actor.TEACHER), branchId);
  private final ReportableMessage message =
      new ReportableMessage(UUID.randomUUID(), UUID.randomUUID(), branchId, teacherId);
  private ReportService service;

  @BeforeEach
  void setUp() {
    service =
        new ReportService(
            reports,
            reportableMessages,
            auditMessages,
            directory,
            new BranchAccessPolicy(),
            new SimpleMeterRegistry(),
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private MessageReport open() {
    return new MessageReport(
        UUID.randomUUID(),
        0L,
        message.messageId(),
        message.conversationId(),
        branchId,
        studentId,
        "rude",
        ReportStatus.OPEN,
        NOW,
        null,
        null,
        null);
  }

  private static ErrorCode codeOf(Runnable action) {
    return catchThrowableOfType(DomainException.class, action::run).getErrorCode();
  }

  @Test
  void aParticipantReportsAMessageOfTheOtherSide() {
    when(reportableMessages.findForReport(student, message.messageId())).thenReturn(message);
    when(reports.existsByMessageAndReporter(message.messageId(), studentId)).thenReturn(false);
    when(reports.save(any())).thenAnswer(i -> i.getArgument(0));

    var view = service.file(student, new FileReportCommand(message.messageId(), "rude"));

    assertThat(view.status()).isEqualTo(ReportStatus.OPEN);
    assertThat(view.branchId()).isEqualTo(branchId);
  }

  @Test
  void reportingOneOwnMessageOrTwiceIsRefused() {
    when(reportableMessages.findForReport(teacherActor, message.messageId())).thenReturn(message);
    when(reportableMessages.findForReport(student, message.messageId())).thenReturn(message);
    lenient()
        .when(reports.existsByMessageAndReporter(message.messageId(), studentId))
        .thenReturn(true);

    assertThat(
            codeOf(
                () -> service.file(teacherActor, new FileReportCommand(message.messageId(), "x"))))
        .isEqualTo(ErrorCode.OWN_MESSAGE_REPORT);
    assertThat(codeOf(() -> service.file(student, new FileReportCommand(message.messageId(), "x"))))
        .isEqualTo(ErrorCode.REPORT_ALREADY_EXISTS);
    verify(reports, never()).save(any());
  }

  @Test
  void aBranchAdminReadsOnlyReportsOfTheirOwnBranch() {
    MessageReport report = open();
    when(reports.findById(report.getId())).thenReturn(Optional.of(report));

    assertThat(codeOf(() -> service.get(otherBranchAdmin, report.getId())))
        .isEqualTo(ErrorCode.BRANCH_SCOPE_VIOLATION);
    assertThat(codeOf(() -> service.auditMessages(otherBranchAdmin, report.getId(), null, 50)))
        .isEqualTo(ErrorCode.BRANCH_SCOPE_VIOLATION);
    verify(auditMessages, never())
        .auditMessages(any(), any(), org.mockito.ArgumentMatchers.anyInt());
  }

  @Test
  void anAdminClosesOnceAndNotTwice() {
    MessageReport report = open();
    when(reports.findById(report.getId())).thenReturn(Optional.of(report));
    when(reports.save(any())).thenAnswer(i -> i.getArgument(0));

    var closed = service.close(admin, report.getId(), "talked to the teacher");

    assertThat(closed.status()).isEqualTo(ReportStatus.CLOSED);
    assertThat(closed.reviewedBy()).isEqualTo(admin.id());
    assertThat(codeOf(() -> service.close(admin, report.getId(), null)))
        .isEqualTo(ErrorCode.REPORT_ALREADY_CLOSED);
  }

  @Test
  void listingNarrowsAnAdminToTheirBranch() {
    when(reports.findAll(branchId, ReportStatus.OPEN, PageRequest.of(0, 20)))
        .thenReturn(org.springframework.data.domain.Page.empty());

    assertThat(service.list(admin, ReportStatus.OPEN, PageRequest.of(0, 20))).isEmpty();
  }
}
