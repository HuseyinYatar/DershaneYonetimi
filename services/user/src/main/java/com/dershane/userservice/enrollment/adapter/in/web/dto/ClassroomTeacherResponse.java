package com.dershane.userservice.enrollment.adapter.in.web.dto;

import java.util.UUID;

/** Carries personal data: {@code toString} prints identifiers only. */
public record ClassroomTeacherResponse(
    UUID assignmentId,
    UUID teacherId,
    String firstName,
    String lastName,
    UUID subjectId,
    String subjectName) {

  @Override
  public String toString() {
    return "ClassroomTeacherResponse[assignmentId="
        + assignmentId
        + ", teacherId="
        + teacherId
        + "]";
  }
}
