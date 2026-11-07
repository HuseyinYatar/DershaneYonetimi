package com.dershane.taskservice.roster.adapter.in.event;

import com.dershane.taskservice.roster.application.port.in.UnlinkGuardianUseCase;
import com.dershane.taskservice.shared.adapter.in.event.EventHandler;
import com.dershane.taskservice.shared.application.Actor;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Removes the guardian link copy of the user service's {@code GuardianUnlinked}. Only the named
 * guardian's link goes: events are not ordered, so "unlinked A" can arrive after "linked B" and
 * must not remove B.
 */
@Component
@RequiredArgsConstructor
class GuardianUnlinkedHandler implements EventHandler {

  private final UnlinkGuardianUseCase unlinkGuardian;
  private final JsonMapper json;

  @Override
  public String eventType() {
    return "GuardianUnlinked";
  }

  @Override
  public void handle(String eventType, JsonNode data) {
    GuardianLink link = json.treeToValue(data, GuardianLink.class);
    unlinkGuardian.unlink(Actor.SYSTEM, link.studentId(), link.guardianId());
  }

  private record GuardianLink(UUID guardianId, UUID studentId) {}
}
