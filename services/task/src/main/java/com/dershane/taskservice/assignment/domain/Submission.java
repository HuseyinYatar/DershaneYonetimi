package com.dershane.taskservice.assignment.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What the student handed in (diagram: Teslim). The description is free text from a minor: masked
 * in {@code toString}.
 */
public record Submission(
    String description, List<UUID> fileIds, Instant submittedAt, boolean late) {

  @Override
  public String toString() {
    return "Submission[late=" + late + "]";
  }
}
