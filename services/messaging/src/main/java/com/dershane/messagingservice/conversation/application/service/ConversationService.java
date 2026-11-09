package com.dershane.messagingservice.conversation.application.service;

import com.dershane.messagingservice.conversation.application.port.in.ConversationQuery;
import com.dershane.messagingservice.conversation.application.port.in.ConversationView;
import com.dershane.messagingservice.conversation.application.port.in.OpenConversationUseCase;
import com.dershane.messagingservice.conversation.application.port.in.OpenedConversation;
import com.dershane.messagingservice.conversation.application.port.out.ConversationQueryPort;
import com.dershane.messagingservice.conversation.application.port.out.LoadConversationPort;
import com.dershane.messagingservice.conversation.application.port.out.SaveConversationPort;
import com.dershane.messagingservice.conversation.domain.Conversation;
import com.dershane.messagingservice.conversation.domain.ConversationPolicy;
import com.dershane.messagingservice.conversation.domain.Party;
import com.dershane.messagingservice.conversation.domain.PartyRole;
import com.dershane.messagingservice.directory.application.port.in.DirectoryEntryView;
import com.dershane.messagingservice.directory.application.port.in.UserDirectoryQuery;
import com.dershane.messagingservice.shared.application.Actor;
import com.dershane.messagingservice.shared.domain.ErrorCode;
import com.dershane.messagingservice.shared.domain.Rejections;
import io.micrometer.observation.annotation.Observed;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
class ConversationService implements OpenConversationUseCase, ConversationQuery {

  private final UserDirectoryQuery directory;
  private final LoadConversationPort loadConversation;
  private final SaveConversationPort saveConversation;
  private final ConversationQueryPort queries;
  private final ConversationAccess access;

  @Override
  @Transactional
  @Observed(name = "conversation.open")
  public OpenedConversation open(Actor actor, UUID participantId) {
    PartyRole callerRole = roleOf(actor);
    DirectoryEntryView entry =
        directory
            .find(participantId)
            .orElseThrow(
                () ->
                    Rejections.reject(
                        log,
                        ErrorCode.USER_NOT_FOUND,
                        "Participant is not a known student or teacher",
                        "callerId",
                        actor.id(),
                        "participantId",
                        participantId));
    Conversation fresh =
        ConversationPolicy.open(
            new Party(actor.id(), callerRole, actor.branchId(), true),
            new Party(
                entry.userId(),
                PartyRole.valueOf(entry.role().name()),
                entry.branchId(),
                entry.active()));

    var existing = loadConversation.findByPair(fresh.getStudentId(), fresh.getTeacherId());
    boolean created = existing.isEmpty() && saveConversation.insert(fresh);
    Conversation conversation =
        created
            ? fresh
            : existing.orElseGet(
                () ->
                    loadConversation
                        .findByPair(fresh.getStudentId(), fresh.getTeacherId())
                        .orElseThrow());
    if (created) {
      log.atInfo()
          .addKeyValue("conversationId", conversation.getId())
          .addKeyValue("callerId", actor.id())
          .log("Conversation opened");
    }
    return new OpenedConversation(
        queries.findViewOf(conversation.getId(), actor.id()).orElseThrow(), created);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<ConversationView> list(Actor actor, Pageable pageable) {
    return queries.findViewsOf(actor.id(), pageable);
  }

  @Override
  @Transactional(readOnly = true)
  public ConversationView get(Actor actor, UUID conversationId) {
    access.requireParticipant(conversationId, actor.id());
    return queries.findViewOf(conversationId, actor.id()).orElseThrow();
  }

  private static PartyRole roleOf(Actor actor) {
    if (actor.isStudent()) {
      return PartyRole.STUDENT;
    }
    if (actor.isTeacher()) {
      return PartyRole.TEACHER;
    }
    throw Rejections.reject(
        log,
        ErrorCode.ACCESS_DENIED,
        "Only students and teachers can open conversations",
        "callerId",
        actor.id());
  }
}
