package com.dershane.messagingservice.conversation.application.port.in;

import java.util.UUID;

/** What the report module needs to know about a message being reported. */
public record ReportableMessage(
    UUID messageId, UUID conversationId, UUID branchId, UUID senderId) {}
