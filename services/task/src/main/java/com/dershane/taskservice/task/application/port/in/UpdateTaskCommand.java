package com.dershane.taskservice.task.application.port.in;

import java.util.List;
import java.util.UUID;

/** Only the texts and attachments of a task can change. */
public record UpdateTaskCommand(String title, String description, List<UUID> attachmentFileIds) {

  public UpdateTaskCommand {
    attachmentFileIds = attachmentFileIds == null ? List.of() : attachmentFileIds;
  }

  @Override
  public String toString() {
    return "UpdateTaskCommand[attachments=" + attachmentFileIds.size() + "]";
  }
}
