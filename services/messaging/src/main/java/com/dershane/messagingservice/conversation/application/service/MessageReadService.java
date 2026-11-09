package com.dershane.messagingservice.conversation.application.service;

import com.dershane.messagingservice.conversation.application.port.in.AuditMessageQuery;
import com.dershane.messagingservice.conversation.application.port.in.AuditMessageView;
import com.dershane.messagingservice.conversation.application.port.in.MessagePage;
import com.dershane.messagingservice.conversation.application.port.in.MessageQuery;
import com.dershane.messagingservice.conversation.application.port.in.MessageSyncCriteria;
import com.dershane.messagingservice.conversation.application.port.in.ReportableMessage;
import com.dershane.messagingservice.conversation.application.port.in.ReportableMessageQuery;
import com.dershane.messagingservice.conversation.application.port.out.ConversationQueryPort;
import com.dershane.messagingservice.shared.application.Actor;
import com.dershane.messagingservice.shared.domain.ErrorCode;
import com.dershane.messagingservice.shared.domain.Rejections;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
class MessageReadService implements MessageQuery, ReportableMessageQuery, AuditMessageQuery {

  /**
   * A client's next sync starts at {@code syncedAt}. A transaction that started before it but
   * committed after the query would be missed, so the mark is set this far back; a message seen
   * twice is simply overwritten by id.
   */
  private static final Duration SYNC_SAFETY_MARGIN = Duration.ofSeconds(10);

  private final ConversationAccess access;
  private final ConversationQueryPort queries;
  private final Clock clock;

  @Override
  @Transactional(readOnly = true)
  public MessagePage list(Actor actor, UUID conversationId, MessageSyncCriteria criteria) {
    access.requireParticipant(conversationId, actor.id());
    if (criteria.beforeSeq() != null && criteria.afterSeq() != null) {
      throw Rejections.reject(
          log,
          ErrorCode.VALIDATION_FAILED,
          "beforeSeq and afterSeq cannot be combined",
          "conversationId",
          conversationId);
    }
    var rows = queries.findMessageRows(conversationId, criteria);
    return new MessagePage(
        rows.messages(), rows.hasMore(), clock.instant().minus(SYNC_SAFETY_MARGIN));
  }

  @Override
  @Transactional(readOnly = true)
  public ReportableMessage findForReport(Actor actor, UUID messageId) {
    ReportableMessage message =
        queries
            .findReportable(messageId)
            .orElseThrow(
                () ->
                    Rejections.reject(
                        log,
                        ErrorCode.MESSAGE_NOT_FOUND,
                        "Message to report does not exist",
                        "messageId",
                        messageId));
    access.requireParticipant(message.conversationId(), actor.id());
    return message;
  }

  @Override
  @Transactional(readOnly = true)
  public List<AuditMessageView> auditMessages(UUID conversationId, Long beforeSeq, int limit) {
    return queries.findAuditMessages(conversationId, beforeSeq, limit);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<AuditMessageView> auditMessage(UUID messageId) {
    return queries.findAuditMessage(messageId);
  }
}
