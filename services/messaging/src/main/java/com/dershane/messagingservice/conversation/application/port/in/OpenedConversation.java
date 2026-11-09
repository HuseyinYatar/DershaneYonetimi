package com.dershane.messagingservice.conversation.application.port.in;

/**
 * @param created false when the pair already had a conversation
 */
public record OpenedConversation(ConversationView conversation, boolean created) {}
