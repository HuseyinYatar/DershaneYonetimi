package com.dershane.messagingservice.directory.adapter.in.event;

import com.dershane.messagingservice.directory.application.port.in.RecordUserCommand;
import com.dershane.messagingservice.directory.application.port.in.RecordUserUseCase;
import com.dershane.messagingservice.shared.adapter.in.event.EventHandler;
import com.dershane.messagingservice.shared.application.Actor;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Keeps the user copy in step with the user service's {@code UserUpdated}. */
@Component
@RequiredArgsConstructor
class UserUpdatedHandler implements EventHandler {

  private final RecordUserUseCase recordUser;
  private final JsonMapper json;

  @Override
  public String eventType() {
    return "UserUpdated";
  }

  @Override
  public void handle(String eventType, JsonNode data) {
    UserUpdated event = json.treeToValue(data, UserUpdated.class);
    recordUser.record(
        Actor.SYSTEM,
        new RecordUserCommand(
            event.userId(),
            event.role(),
            event.branchId(),
            event.firstName(),
            event.lastName(),
            event.active(),
            event.recordVersion()));
  }

  /**
   * Personal data (name): masked in {@code toString}. Unknown fields (e-mail, phone) are ignored.
   */
  private record UserUpdated(
      UUID userId,
      String role,
      UUID branchId,
      String firstName,
      String lastName,
      boolean active,
      long recordVersion) {

    @Override
    public String toString() {
      return "UserUpdated[userId=" + userId + "]";
    }
  }
}
