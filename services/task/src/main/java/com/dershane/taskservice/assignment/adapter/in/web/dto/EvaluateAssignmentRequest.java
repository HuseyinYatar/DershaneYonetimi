package com.dershane.taskservice.assignment.adapter.in.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** The feedback is free text about a minor: masked in {@code toString}. */
public record EvaluateAssignmentRequest(
    @Min(0) @Max(100) Integer score, @Size(max = 5000) String feedback) {

  @Override
  public String toString() {
    return "EvaluateAssignmentRequest[score=" + score + "]";
  }
}
