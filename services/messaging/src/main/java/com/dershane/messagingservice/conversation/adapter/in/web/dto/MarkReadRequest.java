package com.dershane.messagingservice.conversation.adapter.in.web.dto;

import jakarta.validation.constraints.PositiveOrZero;

public record MarkReadRequest(@PositiveOrZero long seq) {}
