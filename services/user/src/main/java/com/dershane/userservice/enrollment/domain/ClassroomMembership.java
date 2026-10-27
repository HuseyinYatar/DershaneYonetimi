package com.dershane.userservice.enrollment.domain;

import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;

/**
 * A student's stay in a classroom (diagram: SinifUyeligi). Memberships are closed, never deleted,
 * so class history is kept. At most one membership per student is open ({@code endDate == null}).
 */
@Getter
public class ClassroomMembership {

  /** Null until the membership is saved for the first time. */
  private final UUID id;

  /** Version the membership was loaded with; null until saved. Used for optimistic locking. */
  private final Long version;

  private final UUID studentId;
  private final UUID classroomId;
  private final LocalDate startDate;
  private LocalDate endDate;

  /** Rehydrates a saved membership. Use {@link #start} for a new one. */
  public ClassroomMembership(
      UUID id,
      Long version,
      UUID studentId,
      UUID classroomId,
      LocalDate startDate,
      LocalDate endDate) {
    this.id = id;
    this.version = version;
    this.studentId = studentId;
    this.classroomId = classroomId;
    this.startDate = startDate;
    this.endDate = endDate;
  }

  public static ClassroomMembership start(UUID studentId, UUID classroomId, LocalDate startDate) {
    return new ClassroomMembership(null, null, studentId, classroomId, startDate, null);
  }

  public boolean isActive() {
    return endDate == null;
  }

  /** Closes on {@code date}, but never before the start date (moved on the day of joining). */
  public void close(LocalDate date) {
    this.endDate = date.isBefore(startDate) ? startDate : date;
  }
}
