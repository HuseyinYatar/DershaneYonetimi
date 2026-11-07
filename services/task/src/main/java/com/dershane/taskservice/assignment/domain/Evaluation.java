package com.dershane.taskservice.assignment.domain;

import java.time.Instant;
import java.util.UUID;

/** The teacher's verdict (diagram: Degerlendirme). The feedback is free text: masked. */
public record Evaluation(UUID evaluatorId, Integer score, String feedback, Instant evaluatedAt) {

  @Override
  public String toString() {
    return "Evaluation[evaluatorId=" + evaluatorId + "]";
  }
}
