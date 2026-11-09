package com.dershane.messagingservice.conversation.adapter.in.web;

import com.dershane.messagingservice.conversation.adapter.in.web.dto.ConversationResponse;
import com.dershane.messagingservice.conversation.adapter.in.web.dto.EditMessageRequest;
import com.dershane.messagingservice.conversation.adapter.in.web.dto.MarkReadRequest;
import com.dershane.messagingservice.conversation.adapter.in.web.dto.MessagePageResponse;
import com.dershane.messagingservice.conversation.adapter.in.web.dto.MessageResponse;
import com.dershane.messagingservice.conversation.adapter.in.web.dto.OpenConversationRequest;
import com.dershane.messagingservice.conversation.adapter.in.web.dto.SendMessageRequest;
import com.dershane.messagingservice.conversation.application.port.in.ConversationQuery;
import com.dershane.messagingservice.conversation.application.port.in.DeleteMessageUseCase;
import com.dershane.messagingservice.conversation.application.port.in.EditMessageUseCase;
import com.dershane.messagingservice.conversation.application.port.in.MarkReadUseCase;
import com.dershane.messagingservice.conversation.application.port.in.MessageQuery;
import com.dershane.messagingservice.conversation.application.port.in.MessageSyncCriteria;
import com.dershane.messagingservice.conversation.application.port.in.OpenConversationUseCase;
import com.dershane.messagingservice.conversation.application.port.in.OpenedConversation;
import com.dershane.messagingservice.conversation.application.port.in.SendMessageUseCase;
import com.dershane.messagingservice.shared.adapter.in.security.SecurityRoles;
import com.dershane.messagingservice.shared.adapter.in.web.CurrentActor;
import com.dershane.messagingservice.shared.adapter.in.web.idempotency.Idempotent;
import com.dershane.messagingservice.shared.application.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Conversations")
@RestController
@RequestMapping("/api/v1/conversations")
@PreAuthorize(SecurityRoles.STUDENT_OR_TEACHER)
@RequiredArgsConstructor
class ConversationController {

  private static final int DEFAULT_LIMIT = 50;

  private final OpenConversationUseCase openConversation;
  private final ConversationQuery conversations;
  private final SendMessageUseCase sendMessage;
  private final EditMessageUseCase editMessage;
  private final DeleteMessageUseCase deleteMessage;
  private final MarkReadUseCase markRead;
  private final MessageQuery messages;
  private final ConversationWebMapper mapper;

  @Operation(summary = "Open the conversation with a student or teacher (or get the existing one)")
  @PostMapping
  public ResponseEntity<ConversationResponse> open(
      @CurrentActor Actor actor, @Valid @RequestBody OpenConversationRequest request) {
    OpenedConversation opened = openConversation.open(actor, request.participantId());
    ConversationResponse body = mapper.toResponse(opened.conversation());
    if (opened.created()) {
      return ResponseEntity.created(URI.create("/api/v1/conversations/" + body.id())).body(body);
    }
    return ResponseEntity.ok(body);
  }

  @Operation(summary = "My conversations, most recent message first")
  @GetMapping
  public PagedModel<ConversationResponse> list(@CurrentActor Actor actor, Pageable pageable) {
    return new PagedModel<>(conversations.list(actor, pageable).map(mapper::toResponse));
  }

  @Operation(summary = "Get one of my conversations")
  @GetMapping("/{conversationId}")
  public ConversationResponse get(@CurrentActor Actor actor, @PathVariable UUID conversationId) {
    return mapper.toResponse(conversations.get(actor, conversationId));
  }

  @Operation(
      summary =
          "Messages: latest, older (beforeSeq) or newer plus changed (afterSeq, updatedSince)")
  @GetMapping("/{conversationId}/messages")
  public MessagePageResponse listMessages(
      @CurrentActor Actor actor,
      @PathVariable UUID conversationId,
      @RequestParam(required = false) Long beforeSeq,
      @RequestParam(required = false) Long afterSeq,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant updatedSince,
      @RequestParam(defaultValue = "" + DEFAULT_LIMIT) @Min(1) @Max(200) int limit) {
    return mapper.toResponse(
        messages.list(
            actor,
            conversationId,
            new MessageSyncCriteria(beforeSeq, afterSeq, updatedSince, limit)));
  }

  @Operation(summary = "Send a text or photo message")
  @PostMapping("/{conversationId}/messages")
  @Idempotent
  public ResponseEntity<MessageResponse> send(
      @CurrentActor Actor actor,
      @PathVariable UUID conversationId,
      @Valid @RequestBody SendMessageRequest request) {
    MessageResponse created =
        mapper.toResponse(sendMessage.send(actor, conversationId, mapper.toCommand(request)));
    return ResponseEntity.created(
            URI.create("/api/v1/conversations/" + conversationId + "/messages/" + created.id()))
        .body(created);
  }

  @Operation(summary = "Edit my message (within the edit window)")
  @PatchMapping("/{conversationId}/messages/{messageId}")
  public MessageResponse edit(
      @CurrentActor Actor actor,
      @PathVariable UUID conversationId,
      @PathVariable UUID messageId,
      @Valid @RequestBody EditMessageRequest request) {
    return mapper.toResponse(editMessage.edit(actor, conversationId, messageId, request.content()));
  }

  @Operation(summary = "Delete my message (the record is kept for audit)")
  @DeleteMapping("/{conversationId}/messages/{messageId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @CurrentActor Actor actor, @PathVariable UUID conversationId, @PathVariable UUID messageId) {
    deleteMessage.delete(actor, conversationId, messageId);
  }

  @Operation(summary = "Mark messages up to seq as read")
  @PutMapping("/{conversationId}/read")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void read(
      @CurrentActor Actor actor,
      @PathVariable UUID conversationId,
      @Valid @RequestBody MarkReadRequest request) {
    markRead.markRead(actor, conversationId, request.seq());
  }
}
