package com.dershane.userservice.enrollment.domain;

import com.dershane.userservice.shared.domain.ErrorCode;
import com.dershane.userservice.shared.domain.Rejections;
import java.util.UUID;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/** Teacher teaches a subject in a classroom (diagram: OgretmenSinifAtamasi). */
@Slf4j
@Getter
public class TeacherAssignment {

  public static final int MAX_WEEKLY_HOURS = 40;

  /** Null until the assignment is saved for the first time. */
  private final UUID id;

  /** Version the assignment was loaded with; null until saved. */
  private final Long version;

  private final UUID teacherId;
  private final UUID classroomId;
  private final UUID subjectId;

  /** Lesson hours per week the classroom needs for this subject; 0 means not planned yet. */
  private final int weeklyHours;

  /** Rehydrates a saved assignment. Use {@link #create} for a new one. */
  public TeacherAssignment(
      UUID id, Long version, UUID teacherId, UUID classroomId, UUID subjectId, int weeklyHours) {
    this.id = id;
    this.version = version;
    this.teacherId = teacherId;
    this.classroomId = classroomId;
    this.subjectId = subjectId;
    this.weeklyHours = weeklyHours;
  }

  public static TeacherAssignment create(UUID teacherId, UUID classroomId, UUID subjectId) {
    return new TeacherAssignment(null, null, teacherId, classroomId, subjectId, 0);
  }

  public TeacherAssignment withWeeklyHours(int hours) {
    if (hours < 0 || hours > MAX_WEEKLY_HOURS) {
      throw Rejections.reject(
          log,
          ErrorCode.WEEKLY_HOURS_OUT_OF_RANGE,
          "Weekly hours out of range",
          "assignmentId",
          id,
          "weeklyHours",
          hours);
    }
    return new TeacherAssignment(id, version, teacherId, classroomId, subjectId, hours);
  }
}
