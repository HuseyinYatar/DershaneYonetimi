package com.dershane.messagingservice.conversation.domain;

import com.dershane.messagingservice.shared.domain.ErrorCode;
import com.dershane.messagingservice.shared.domain.Rejections;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

/**
 * Who may talk to whom: exactly one student and one teacher of the same branch. Only the branch is
 * checked, not whether the teacher teaches the student's classroom (the user service sends no such
 * event).
 */
@Slf4j
public final class ConversationPolicy {

  private ConversationPolicy() {}

  /**
   * Checks the pair and opens the conversation between them.
   *
   * @throws com.dershane.messagingservice.shared.domain.DomainException when the pair is not
   *     allowed or the other side is inactive
   */
  public static Conversation open(Party caller, Party other) {
    if (caller.id().equals(other.id())) {
      throw notAllowed("Cannot message oneself", caller, other);
    }
    if (caller.role() == other.role()) {
      throw notAllowed("A conversation needs one student and one teacher", caller, other);
    }
    // A missing branch must never match: null == null would join every branch.
    if (caller.branchId() == null || !Objects.equals(caller.branchId(), other.branchId())) {
      throw notAllowed("Participants are not in the same branch", caller, other);
    }
    if (!other.active()) {
      throw Rejections.reject(
          log,
          ErrorCode.PARTICIPANT_INACTIVE,
          "The other participant is inactive",
          "callerId",
          caller.id(),
          "participantId",
          other.id());
    }
    Party student = caller.role() == PartyRole.STUDENT ? caller : other;
    Party teacher = caller.role() == PartyRole.TEACHER ? caller : other;
    return Conversation.open(caller.branchId(), student.id(), teacher.id());
  }

  private static RuntimeException notAllowed(String reason, Party caller, Party other) {
    return Rejections.reject(
        log,
        ErrorCode.CONVERSATION_NOT_ALLOWED,
        reason,
        "callerId",
        caller.id(),
        "participantId",
        other.id());
  }
}
