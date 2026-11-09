package com.dershane.messagingservice.conversation.application.port.in;

import java.time.Instant;
import java.util.UUID;

/**
 * A conversation as seen by one participant. {@code lastMessage} is null before the first message.
 */
public record ConversationView(
    UUID id,
    ParticipantView otherParticipant,
    long lastSeq,
    long lastReadSeq,
    long unreadCount,
    Instant lastMessageAt,
    LastMessageView lastMessage) {}
