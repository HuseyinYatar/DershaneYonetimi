package com.dershane.messagingservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.dershane.messagingservice.conversation.adapter.in.web.dto.ConversationResponse;
import com.dershane.messagingservice.conversation.adapter.in.web.dto.EditMessageRequest;
import com.dershane.messagingservice.conversation.adapter.in.web.dto.LastMessageResponse;
import com.dershane.messagingservice.conversation.adapter.in.web.dto.MessageResponse;
import com.dershane.messagingservice.conversation.adapter.in.web.dto.ParticipantResponse;
import com.dershane.messagingservice.conversation.adapter.in.web.dto.SendMessageRequest;
import com.dershane.messagingservice.conversation.application.port.in.AuditEditView;
import com.dershane.messagingservice.conversation.application.port.in.AuditMessageView;
import com.dershane.messagingservice.conversation.application.port.in.LastMessageView;
import com.dershane.messagingservice.conversation.application.port.in.MessageView;
import com.dershane.messagingservice.conversation.application.port.in.ParticipantView;
import com.dershane.messagingservice.conversation.application.port.in.SendMessageCommand;
import com.dershane.messagingservice.conversation.domain.Message;
import com.dershane.messagingservice.conversation.domain.MessageType;
import com.dershane.messagingservice.directory.application.port.in.DirectoryEntryView;
import com.dershane.messagingservice.directory.application.port.in.DirectoryRole;
import com.dershane.messagingservice.directory.application.port.in.RecordUserCommand;
import com.dershane.messagingservice.directory.domain.UserReplica;
import com.dershane.messagingservice.directory.domain.UserRole;
import com.dershane.messagingservice.report.adapter.in.web.dto.AuditEditResponse;
import com.dershane.messagingservice.report.adapter.in.web.dto.AuditMessageResponse;
import com.dershane.messagingservice.report.adapter.in.web.dto.CloseReportRequest;
import com.dershane.messagingservice.report.adapter.in.web.dto.FileReportRequest;
import com.dershane.messagingservice.report.adapter.in.web.dto.PartyResponse;
import com.dershane.messagingservice.report.adapter.in.web.dto.ReportResponse;
import com.dershane.messagingservice.report.application.port.in.FileReportCommand;
import com.dershane.messagingservice.report.application.port.in.PartyView;
import com.dershane.messagingservice.report.application.port.in.ReportView;
import com.dershane.messagingservice.report.domain.MessageReport;
import com.dershane.messagingservice.report.domain.ReportStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * KVKK: objects carrying names or message text may end up in a log line through string
 * concatenation or an exception message. Their {@code toString} must not reveal them.
 */
class PersonalDataMaskingTest {

  private static final String NAME = "Zeynep Öztürk";
  private static final String FIRST = "Zeynep";
  private static final String LAST = "Öztürk";
  private static final String TEXT = "I do not understand question seven";
  private static final List<String> SECRETS = List.of(FIRST, LAST, TEXT);

  private final UUID id = UUID.randomUUID();
  private final Instant now = Instant.parse("2026-11-09T08:00:00Z");

  private void assertMasked(Object value) {
    assertThat(value.toString()).doesNotContain(SECRETS).doesNotContain(NAME);
  }

  @Test
  void directoryObjectsAreMasked() {
    assertMasked(new DirectoryEntryView(id, DirectoryRole.STUDENT, id, FIRST, LAST, true));
    assertMasked(new RecordUserCommand(id, "STUDENT", id, FIRST, LAST, true, 1));
    assertMasked(new UserReplica(id, 0L, UserRole.STUDENT, id, FIRST, LAST, true, 1));
  }

  @Test
  void messageObjectsAreMasked() {
    assertMasked(Message.create(id, 1, id, MessageType.TEXT, TEXT, null, now));
    assertMasked(new SendMessageCommand(MessageType.TEXT, TEXT, null));
    assertMasked(new SendMessageRequest(MessageType.TEXT, TEXT, null));
    assertMasked(new EditMessageRequest(TEXT));
    assertMasked(
        new MessageView(id, id, 1, id, MessageType.TEXT, TEXT, null, now, null, false, now));
    assertMasked(
        new MessageResponse(id, id, 1, id, MessageType.TEXT, TEXT, null, now, null, false, now));
  }

  @Test
  void conversationObjectsAreMasked() {
    ParticipantView participant = new ParticipantView(id, FIRST, LAST, DirectoryRole.TEACHER, true);
    LastMessageView last = new LastMessageView(1, id, MessageType.TEXT, TEXT, false, now);
    assertMasked(participant);
    assertMasked(last);
    assertMasked(new ParticipantResponse(id, FIRST, LAST, DirectoryRole.TEACHER, true));
    assertMasked(new LastMessageResponse(1, id, MessageType.TEXT, TEXT, false, now));
    assertMasked(new ConversationResponse(id, null, 1, 0, 1, now, null));
  }

  @Test
  void reportObjectsAreMasked() {
    AuditMessageView audit =
        new AuditMessageView(
            id,
            1,
            id,
            MessageType.TEXT,
            TEXT,
            null,
            now,
            now,
            true,
            now,
            List.of(new AuditEditView(TEXT, now)));
    assertMasked(audit);
    assertMasked(new AuditEditView(TEXT, now));
    assertMasked(new PartyView(id, FIRST, LAST));
    assertMasked(new FileReportCommand(id, TEXT));
    assertMasked(new FileReportRequest(id, TEXT));
    assertMasked(new CloseReportRequest(TEXT));
    assertMasked(MessageReport.file(id, id, id, UUID.randomUUID(), id, TEXT, now));
    assertMasked(
        new ReportView(
            id,
            ReportStatus.OPEN,
            id,
            id,
            id,
            new PartyView(id, FIRST, LAST),
            new PartyView(id, FIRST, LAST),
            TEXT,
            now,
            null,
            null,
            TEXT,
            audit));
    assertMasked(new PartyResponse(id, FIRST, LAST));
    assertMasked(new AuditEditResponse(TEXT, now));
    assertMasked(
        new AuditMessageResponse(
            id, 1, id, MessageType.TEXT, TEXT, null, now, now, true, now, List.of()));
    assertMasked(
        new ReportResponse(
            id,
            ReportStatus.OPEN,
            id,
            id,
            id,
            new PartyResponse(id, FIRST, LAST),
            new PartyResponse(id, FIRST, LAST),
            TEXT,
            now,
            null,
            null,
            TEXT,
            null));
  }
}
