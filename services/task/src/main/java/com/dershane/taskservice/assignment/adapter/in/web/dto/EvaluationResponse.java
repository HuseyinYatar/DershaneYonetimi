package com.dershane.taskservice.assignment.adapter.in.web.dto;

import java.time.Instant;
import java.util.UUID;

/** The feedback is free text about a minor: masked in {@code toString}. */
public record EvaluationResponse(
    UUID evaluatorId, Integer score, String feedback, Instant evaluatedAt) {

  @Override
  public String toString() {
    return "EvaluationResponse[evaluatorId=" + evaluatorId + "]";
  }
}
