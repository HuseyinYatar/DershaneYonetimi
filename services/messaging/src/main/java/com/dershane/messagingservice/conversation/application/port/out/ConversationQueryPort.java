package com.dershane.messagingservice.conversation.application.port.out;

import com.dershane.messagingservice.conversation.application.port.in.AuditMessageView;
import com.dershane.messagingservice.conversation.application.port.in.ConversationView;
import com.dershane.messagingservice.conversation.application.port.in.MessageSyncCriteria;
import com.dershane.messagingservice.conversation.application.port.in.MessageView;
import com.dershane.messagingservice.conversation.application.port.in.ReportableMessage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Read side in plain SQL. */
public interface ConversationQueryPort {

  Page<ConversationView> findViewsOf(UUID userId, Pageable pageable);

  Optional<ConversationView> findViewOf(UUID conversationId, UUID userId);

  /** Messages in ascending seq order for a sync request. */
  MessageRows findMessageRows(UUID conversationId, MessageSyncCriteria criteria);

  Optional<ReportableMessage> findReportable(UUID messageId);

  /** The latest {@code limit} messages before {@code beforeSeq}, ascending, with audit detail. */
  List<AuditMessageView> findAuditMessages(UUID conversationId, Long beforeSeq, int limit);

  Optional<AuditMessageView> findAuditMessage(UUID messageId);

  /**
   * @param hasMore more messages exist beyond the returned ones
   */
  record MessageRows(List<MessageView> messages, boolean hasMore) {}
}
