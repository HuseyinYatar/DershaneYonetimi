package com.dershane.taskservice.roster.adapter.in.event;

import com.dershane.taskservice.roster.application.port.in.LinkGuardianUseCase;
import com.dershane.taskservice.roster.application.port.in.RecordMembershipAddedCommand;
import com.dershane.taskservice.roster.application.port.in.RecordMembershipAddedUseCase;
import com.dershane.taskservice.roster.application.port.in.RecordMembershipRemovedUseCase;
import com.dershane.taskservice.roster.application.port.in.UnlinkGuardianUseCase;
import com.dershane.taskservice.shared.adapter.in.event.EventHandler;
import com.dershane.taskservice.shared.application.Actor;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Keeps the local copies of memberships and guardian links in step with the user service's events
 * (schemas in {@code shared/events/user}). The user service already authorized each change, so the
 * use cases run as {@link Actor#SYSTEM}.
 */
@Component
@RequiredArgsConstructor
class UserEventHandler implements EventHandler {

  private static final String STUDENT_ADDED = "StudentAddedToClassroom";
  private static final String STUDENT_REMOVED = "StudentRemovedFromClassroom";
  private static final String GUARDIAN_LINKED = "GuardianLinked";
  private static final String GUARDIAN_UNLINKED = "GuardianUnlinked";

  private final RecordMembershipAddedUseCase recordAdded;
  private final RecordMembershipRemovedUseCase recordRemoved;
  private final LinkGuardianUseCase linkGuardian;
  private final UnlinkGuardianUseCase unlinkGuardian;
  private final JsonMapper json;

  @Override
  public Set<String> eventTypes() {
    return Set.of(STUDENT_ADDED, STUDENT_REMOVED, GUARDIAN_LINKED, GUARDIAN_UNLINKED);
  }

  @Override
  public void handle(String eventType, JsonNode data) {
    switch (eventType) {
      case STUDENT_ADDED ->
          // The event carries exactly the fields of the command.
          recordAdded.recordAdded(
              Actor.SYSTEM, json.treeToValue(data, RecordMembershipAddedCommand.class));
      case STUDENT_REMOVED -> {
        MembershipEnded ended = json.treeToValue(data, MembershipEnded.class);
        recordRemoved.recordRemoved(Actor.SYSTEM, ended.membershipId(), ended.endDate());
      }
      case GUARDIAN_LINKED -> {
        GuardianChange change = json.treeToValue(data, GuardianChange.class);
        linkGuardian.link(Actor.SYSTEM, change.guardianId(), change.studentId());
      }
      // Only the named guardian's link goes: events are not ordered, so "unlinked A" can arrive
      // after "linked B" and must not remove B.
      case GUARDIAN_UNLINKED -> {
        GuardianChange change = json.treeToValue(data, GuardianChange.class);
        unlinkGuardian.unlink(Actor.SYSTEM, change.studentId(), change.guardianId());
      }
      default -> throw new IllegalArgumentException("Unsupported event type " + eventType);
    }
  }

  private record MembershipEnded(UUID membershipId, LocalDate endDate) {}

  private record GuardianChange(UUID guardianId, UUID studentId) {}
}
