package com.dershane.taskservice.assignment.application.port.in;

import java.time.Instant;
import java.util.UUID;

/** The feedback is free text about a minor: masked in {@code toString}. */
public record EvaluationView(
    UUID evaluatorId, Integer score, String feedback, Instant evaluatedAt) {

  @Override
  public String toString() {
    return "EvaluationView[evaluatorId=" + evaluatorId + "]";
  }
}
