package com.dershane.messagingservice.conversation.application.port.in;

import java.time.Instant;
import java.util.List;

/**
 * Messages in ascending {@code seq} order. The client sends {@code syncedAt} back as {@code
 * updatedSince} on its next sync.
 */
public record MessagePage(List<MessageView> messages, boolean hasMore, Instant syncedAt) {}
