package com.dershane.taskservice.assignment.application.port.in;

/** The feedback is free text about a minor: masked in {@code toString}. */
public record EvaluateAssignmentCommand(Integer score, String feedback) {

  @Override
  public String toString() {
    return "EvaluateAssignmentCommand[score=" + score + "]";
  }
}
