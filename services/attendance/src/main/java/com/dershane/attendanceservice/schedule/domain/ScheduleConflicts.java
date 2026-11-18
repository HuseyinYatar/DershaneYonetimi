package com.dershane.attendanceservice.schedule.domain;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * The one rule for "is someone in two places at once": two lessons on the same day whose times
 * overlap clash when they share the classroom or the teacher. Used for drafts, and meant for
 * schedules too.
 */
public final class ScheduleConflicts {

  public enum Kind {
    CLASSROOM,
    TEACHER
  }

  /** A weekly lesson. {@code id} may be null for one that is not saved yet. */
  public record Lesson(
      UUID id,
      UUID classroomId,
      UUID teacherId,
      DayOfWeek dayOfWeek,
      LocalTime startsAt,
      LocalTime endsAt) {

    boolean overlaps(Lesson other) {
      return dayOfWeek == other.dayOfWeek
          && startsAt.isBefore(other.endsAt)
          && other.startsAt.isBefore(endsAt);
    }
  }

  private ScheduleConflicts() {}

  /**
   * What {@code candidate} clashes with among {@code others} (a lesson with the candidate's own id
   * is ignored). The classroom is reported before the teacher.
   */
  public static Optional<Kind> find(Lesson candidate, Collection<Lesson> others) {
    boolean teacherBusy = false;
    for (Lesson other : others) {
      boolean itself = candidate.id() != null && candidate.id().equals(other.id());
      if (itself || !candidate.overlaps(other)) {
        continue;
      }
      if (other.classroomId().equals(candidate.classroomId())) {
        return Optional.of(Kind.CLASSROOM);
      }
      teacherBusy = teacherBusy || other.teacherId().equals(candidate.teacherId());
    }
    return teacherBusy ? Optional.of(Kind.TEACHER) : Optional.empty();
  }
}
