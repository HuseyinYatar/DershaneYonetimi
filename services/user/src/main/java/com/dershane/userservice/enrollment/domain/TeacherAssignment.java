package com.dershane.userservice.enrollment.domain;

import java.util.UUID;
import lombok.Getter;

/** Teacher teaches a subject in a classroom (diagram: OgretmenSinifAtamasi). */
@Getter
public class TeacherAssignment {

  /** Null until the assignment is saved for the first time. */
  private final UUID id;

  /** Version the assignment was loaded with; null until saved. */
  private final Long version;

  private final UUID teacherId;
  private final UUID classroomId;
  private final UUID subjectId;

  /** Rehydrates a saved assignment. Use {@link #create} for a new one. */
  public TeacherAssignment(
      UUID id, Long version, UUID teacherId, UUID classroomId, UUID subjectId) {
    this.id = id;
    this.version = version;
    this.teacherId = teacherId;
    this.classroomId = classroomId;
    this.subjectId = subjectId;
  }

  public static TeacherAssignment create(UUID teacherId, UUID classroomId, UUID subjectId) {
    return new TeacherAssignment(null, null, teacherId, classroomId, subjectId);
  }
}
