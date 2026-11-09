package com.dershane.messagingservice.conversation.application.port.in;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditMessageQuery {

  /**
   * Messages of a conversation, in ascending seq order, the latest {@code limit} before {@code
   * beforeSeq} (or before the end), with deleted content and edit history. It checks nothing about
   * the caller: the report module authorizes.
   */
  List<AuditMessageView> auditMessages(UUID conversationId, Long beforeSeq, int limit);

  /** One message with its audit detail (the reported message in a report view). */
  Optional<AuditMessageView> auditMessage(UUID messageId);
}
