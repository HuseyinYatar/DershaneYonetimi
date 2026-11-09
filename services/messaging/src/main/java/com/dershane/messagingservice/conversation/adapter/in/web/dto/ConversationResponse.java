package com.dershane.messagingservice.conversation.adapter.in.web.dto;

import java.time.Instant;
import java.util.UUID;

public record ConversationResponse(
    UUID id,
    ParticipantResponse otherParticipant,
    long lastSeq,
    long lastReadSeq,
    long unreadCount,
    Instant lastMessageAt,
    LastMessageResponse lastMessage) {}
