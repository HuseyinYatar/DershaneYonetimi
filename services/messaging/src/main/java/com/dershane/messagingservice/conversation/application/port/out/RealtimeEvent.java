package com.dershane.messagingservice.conversation.application.port.out;

import com.dershane.messagingservice.conversation.application.port.in.MessageView;
import java.util.Set;
import java.util.UUID;

/** Something to push to connected clients. The push is best-effort; clients also sync by query. */
public sealed interface RealtimeEvent
    permits RealtimeEvent.MessageChanged, RealtimeEvent.ReadAdvanced {

  Set<UUID> recipients();

  /** A message was created, edited or deleted; clients upsert it by id. */
  record MessageChanged(Set<UUID> recipients, MessageView message) implements RealtimeEvent {}

  /** A participant moved their read position. */
  record ReadAdvanced(Set<UUID> recipients, UUID conversationId, UUID userId, long lastReadSeq)
      implements RealtimeEvent {}
}
