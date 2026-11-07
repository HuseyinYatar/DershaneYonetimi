package com.dershane.taskservice.roster.adapter.in.event;

import com.dershane.taskservice.roster.application.port.in.RecordMembershipAddedCommand;
import com.dershane.taskservice.roster.application.port.in.RecordMembershipAddedUseCase;
import com.dershane.taskservice.shared.adapter.in.event.EventHandler;
import com.dershane.taskservice.shared.application.Actor;
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
  public void handle(String eventType, JsonNode data) {
    // The event carries exactly the fields of the command.
    recordAdded.recordAdded(
        Actor.SYSTEM, json.treeToValue(data, RecordMembershipAddedCommand.class));
  }
}
