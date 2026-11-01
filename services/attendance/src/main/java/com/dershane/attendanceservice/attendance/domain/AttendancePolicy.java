package com.dershane.attendanceservice.attendance.domain;

import com.dershane.attendanceservice.shared.domain.ErrorCode;
import com.dershane.attendanceservice.shared.domain.Rejections;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

/** Rules for submitting and correcting attendance that involve no single record. */
@Slf4j
public final class AttendancePolicy {

  private AttendancePolicy() {}

  /** Status and excuse a submitted entry ends up with. */
  public record Outcome(AttendanceStatus status, UUID excuseId) {}

  /**
   * Applies a covering approved excuse: an {@code ABSENT} or {@code EXCUSED} mark becomes {@code
   * EXCUSED} linked to that excuse (the guardian already told the school, so nobody is notified).
   * {@code PRESENT} and {@code LATE} stay as they are.
   *
   * @param coveringExcuseId the approved excuse covering the student on the session day, or null
   */
  public static Outcome resolve(AttendanceStatus requested, UUID coveringExcuseId) {
    boolean missing = requested == AttendanceStatus.ABSENT || requested == AttendanceStatus.EXCUSED;
    if (missing && coveringExcuseId != null) {
      return new Outcome(AttendanceStatus.EXCUSED, coveringExcuseId);
    }
    return new Outcome(requested, null);
  }

  /**
   * The submitted list must name every student of the roster exactly once and nobody else. The
   * server never fills in a default, so a forgotten student cannot silently count as present.
   */
  public static void requireExactRoster(UUID sessionId, Set<UUID> roster, List<UUID> submitted) {
    Set<UUID> seen = new HashSet<>();
    Set<UUID> duplicates = new HashSet<>();
    for (UUID studentId : submitted) {
      if (!seen.add(studentId)) {
        duplicates.add(studentId);
      }
    }
    Set<UUID> missing = new HashSet<>(roster);
    missing.removeAll(seen);
    Set<UUID> unknown = new HashSet<>(seen);
    unknown.removeAll(roster);
    if (missing.isEmpty() && unknown.isEmpty() && duplicates.isEmpty()) {
      return;
    }
    throw Rejections.reject(
        log,
        ErrorCode.ROSTER_MISMATCH,
        "Attendance list does not match the roster",
        "sessionId",
        sessionId,
        "missingStudentIds",
        missing,
        "unknownStudentIds",
        unknown,
        "duplicateStudentIds",
        duplicates);
  }

  /** A teacher may correct a record until {@code windowDays} days after the session day. */
  public static void requireWithinCorrectionWindow(
      UUID sessionId, LocalDate sessionDate, LocalDate today, int windowDays) {
    long daysSince = ChronoUnit.DAYS.between(sessionDate, today);
    if (daysSince > windowDays) {
      throw Rejections.reject(
          log,
          ErrorCode.CORRECTION_WINDOW_CLOSED,
          "Correction rejected: the teacher's correction window has passed",
          "sessionId",
          sessionId,
          "daysSinceSession",
          daysSince,
          "windowDays",
          windowDays);
    }
  }
}
