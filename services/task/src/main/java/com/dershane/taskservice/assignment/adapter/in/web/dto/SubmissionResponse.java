package com.dershane.taskservice.assignment.adapter.in.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The description is free text from a minor: masked in {@code toString}. */
public record SubmissionResponse(
    String description, List<UUID> fileIds, Instant submittedAt, boolean late) {

  @Override
  public String toString() {
    return "SubmissionResponse[late=" + late + "]";
  }
}
