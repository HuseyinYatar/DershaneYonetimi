package com.dershane.taskservice.assignment.adapter.in.web.dto;

import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** The description is free text from a minor: masked in {@code toString}. */
public record SubmitAssignmentRequest(
    @Size(max = 5000) String description, @Size(max = 10) List<UUID> fileIds) {

  @Override
  public String toString() {
    return "SubmitAssignmentRequest[files=" + (fileIds == null ? 0 : fileIds.size()) + "]";
  }
}
