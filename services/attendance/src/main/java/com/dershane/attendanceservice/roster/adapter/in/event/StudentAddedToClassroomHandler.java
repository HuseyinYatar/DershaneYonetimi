package com.dershane.attendanceservice.roster.adapter.in.event;

import com.dershane.attendanceservice.roster.application.port.in.RecordMembershipAddedCommand;
import com.dershane.attendanceservice.roster.application.port.in.RecordMembershipAddedUseCase;
import com.dershane.attendanceservice.shared.adapter.in.event.EventHandler;
import com.dershane.attendanceservice.shared.application.Actor;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Records a membership copy from the user service's {@code StudentAddedToClassroom} (schema in
 * {@code shared/events/user}). The user service already authorized the change, so the use case runs
 * as {@link Actor#SYSTEM}.
 */
@Component
@RequiredArgsConstructor
class StudentAddedToClassroomHandler implements EventHandler {

  private final RecordMembershipAddedUseCase recordAdded;
  private final JsonMapper json;

  @Override
  public String eventType() {
    return "StudentAddedToClassroom";
  }

  @Override
  public void handle(String eventType, Instant occurredAt, JsonNode data) {
    // The event carries exactly the fields of the command.
    recordAdded.recordAdded(
        Actor.SYSTEM, json.treeToValue(data, RecordMembershipAddedCommand.class));
  }
}
