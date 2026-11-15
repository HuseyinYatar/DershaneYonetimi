package com.dershane.attendanceservice.roster.adapter.in.event;

import com.dershane.attendanceservice.roster.application.port.in.LinkGuardianUseCase;
import com.dershane.attendanceservice.shared.adapter.in.event.EventHandler;
import com.dershane.attendanceservice.shared.application.Actor;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Records the guardian link copy of the user service's {@code GuardianLinked}. */
@Component
@RequiredArgsConstructor
class GuardianLinkedHandler implements EventHandler {

  private final LinkGuardianUseCase linkGuardian;
  private final JsonMapper json;

  @Override
  public String eventType() {
    return "GuardianLinked";
  }

  @Override
  public void handle(String eventType, Instant occurredAt, JsonNode data) {
    GuardianLink link = json.treeToValue(data, GuardianLink.class);
    linkGuardian.link(Actor.SYSTEM, link.guardianId(), link.studentId(), occurredAt);
  }

  private record GuardianLink(UUID guardianId, UUID studentId) {}
}
