package com.dershane.taskservice.assignment.application.port.in;

import java.util.List;
import java.util.UUID;

/** The description is free text from a minor: masked in {@code toString}. */
public record SubmitAssignmentCommand(String description, List<UUID> fileIds) {

  public SubmitAssignmentCommand {
    fileIds = fileIds == null ? List.of() : fileIds;
  }

  @Override
  public String toString() {
    return "SubmitAssignmentCommand[files=" + fileIds.size() + "]";
  }
}
