package com.dershane.userservice.enrollment.application.port.in;

import java.util.UUID;

/** Carries personal data: {@code toString} prints identifiers only. */
public record ClassroomTeacherView(
    UUID assignmentId,
    UUID teacherId,
    String firstName,
    String lastName,
    UUID subjectId,
    String subjectName) {

  @Override
  public String toString() {
    return "ClassroomTeacherView[assignmentId=" + assignmentId + ", teacherId=" + teacherId + "]";
  }
}
