package com.dershane.messagingservice.conversation.adapter.in.web.dto;

import java.time.Instant;
import java.util.List;

public record MessagePageResponse(
    List<MessageResponse> messages, boolean hasMore, Instant syncedAt) {}
