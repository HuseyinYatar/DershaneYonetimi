package com.dershane.messagingservice.conversation.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record OpenConversationRequest(@NotNull UUID participantId) {}
