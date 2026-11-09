package com.dershane.attendanceservice.roster.adapter.in.event;

import com.dershane.attendanceservice.roster.application.port.in.RecordMembershipRemovedUseCase;
import com.dershane.attendanceservice.shared.adapter.in.event.EventHandler;
import com.dershane.attendanceservice.shared.application.Actor;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Closes a membership copy on the end date of the user service's {@code
 * StudentRemovedFromClassroom}.
 */
@Component
@RequiredArgsConstructor
class StudentRemovedFromClassroomHandler implements EventHandler {

  private final RecordMembershipRemovedUseCase recordRemoved;
  private final JsonMapper json;

  @Override
  public String eventType() {
    return "StudentRemovedFromClassroom";
  }

  @Override
  public void handle(String eventType, Instant occurredAt, JsonNode data) {
    MembershipEnded ended = json.treeToValue(data, MembershipEnded.class);
    recordRemoved.recordRemoved(Actor.SYSTEM, ended.membershipId(), ended.endDate());
  }

  private record MembershipEnded(UUID membershipId, LocalDate endDate) {}
}
