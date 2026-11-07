package com.dershane.taskservice.roster.domain;

import com.dershane.taskservice.shared.domain.ErrorCode;
import com.dershane.taskservice.shared.domain.Rejections;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * A student's stay in a classroom, copied from the user service ({@code ClassroomMembership}). Its
 * id is the user service's membership id, so a repeated event updates the same row. The dates are
 * inclusive: a student is on the roster of every session from {@code startDate} to {@code endDate}.
 */
@Slf4j
@Getter
public class MembershipReplica {

  private final UUID id;

  /** Version the replica was loaded with; null until saved. Used for optimistic locking. */
  private final Long version;

  private final UUID classroomId;
  private final UUID studentId;
  private final UUID branchId;
  private final LocalDate startDate;
  private LocalDate endDate;

  // Personal data: never logged, masked in toString.
  private final String studentNumber;
  private final String fullName;

  public MembershipReplica(
      UUID id,
      Long version,
      UUID classroomId,
      UUID studentId,
      UUID branchId,
      LocalDate startDate,
      LocalDate endDate,
      String studentNumber,
      String fullName) {
    this.id = id;
    this.version = version;
    this.classroomId = classroomId;
    this.studentId = studentId;
    this.branchId = branchId;
    this.startDate = startDate;
    this.endDate = endDate;
    this.studentNumber = studentNumber;
    this.fullName = fullName;
  }

  public static MembershipReplica open(
      UUID id,
      UUID classroomId,
      UUID studentId,
      UUID branchId,
      LocalDate startDate,
      String studentNumber,
      String fullName) {
    return new MembershipReplica(
        id, null, classroomId, studentId, branchId, startDate, null, studentNumber, fullName);
  }

  public boolean activeOn(LocalDate date) {
    return !date.isBefore(startDate) && (endDate == null || !date.isAfter(endDate));
  }

  /**
   * Closes the membership on {@code newEndDate} (inclusive).
   *
   * @return false when it was already closed on that date (a repeated event; nothing changed)
   */
  public boolean close(LocalDate newEndDate) {
    if (newEndDate.isBefore(startDate)) {
      throw Rejections.reject(
          log,
          ErrorCode.INVALID_DATE_RANGE,
          "Membership end date is before its start date",
          "membershipId",
          id);
    }
    if (newEndDate.equals(endDate)) {
      return false;
    }
    this.endDate = newEndDate;
    return true;
  }

  @Override
  public String toString() {
    return "MembershipReplica[id=" + id + "]";
  }
}
