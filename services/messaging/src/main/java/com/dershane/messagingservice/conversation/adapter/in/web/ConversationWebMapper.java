package com.dershane.messagingservice.conversation.adapter.in.web;

import com.dershane.messagingservice.conversation.adapter.in.web.dto.ConversationResponse;
import com.dershane.messagingservice.conversation.adapter.in.web.dto.MessagePageResponse;
import com.dershane.messagingservice.conversation.adapter.in.web.dto.MessageResponse;
import com.dershane.messagingservice.conversation.adapter.in.web.dto.SendMessageRequest;
import com.dershane.messagingservice.conversation.application.port.in.ConversationView;
import com.dershane.messagingservice.conversation.application.port.in.MessagePage;
import com.dershane.messagingservice.conversation.application.port.in.MessageView;
import com.dershane.messagingservice.conversation.application.port.in.SendMessageCommand;
import org.mapstruct.Mapper;

@Mapper
interface ConversationWebMapper {

  SendMessageCommand toCommand(SendMessageRequest request);

  ConversationResponse toResponse(ConversationView view);

  MessageResponse toResponse(MessageView view);

  MessagePageResponse toResponse(MessagePage page);
}
