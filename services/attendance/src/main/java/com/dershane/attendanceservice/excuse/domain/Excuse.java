package com.dershane.attendanceservice.excuse.domain;

import com.dershane.attendanceservice.shared.domain.ErrorCode;
import com.dershane.attendanceservice.shared.domain.Rejections;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * A guardian's notice that a student misses lessons from {@code fromDate} to {@code toDate}
 * (inclusive). Once an admin decided, it never changes.
 */
@Slf4j
@Getter
public class Excuse {

  /** Null until the excuse is saved for the first time. */
  private final UUID id;

  /** Version the excuse was loaded with; null until saved. Used for optimistic locking. */
  private final Long version;

  private final UUID studentId;
  private final UUID branchId;
  private final LocalDate fromDate;
  private final LocalDate toDate;

  // Free text about a child: never logged, masked in toString.
  private final String reason;

  private ExcuseStatus status;
  private final UUID requestedBy;
  private UUID decidedBy;
  private Instant decidedAt;
  private String decisionNote;

  /** Rehydrates a saved excuse. Use {@link #request} for a new one. */
  // A rehydrating constructor takes every field of the aggregate.
  @SuppressWarnings("checkstyle:ParameterNumber")
  public Excuse(
      UUID id,
      Long version,
      UUID studentId,
      UUID branchId,
      LocalDate fromDate,
      LocalDate toDate,
      String reason,
      ExcuseStatus status,
      UUID requestedBy,
      UUID decidedBy,
      Instant decidedAt,
      String decisionNote) {
    this.id = id;
    this.version = version;
    this.studentId = studentId;
    this.branchId = branchId;
    this.fromDate = fromDate;
    this.toDate = toDate;
    this.reason = reason;
    this.status = status;
    this.requestedBy = requestedBy;
    this.decidedBy = decidedBy;
    this.decidedAt = decidedAt;
    this.decisionNote = decisionNote;
  }

  public static Excuse request(
      UUID studentId,
      UUID branchId,
      LocalDate fromDate,
      LocalDate toDate,
      String reason,
      UUID requestedBy) {
    if (toDate.isBefore(fromDate)) {
      throw Rejections.reject(
          log,
          ErrorCode.INVALID_DATE_RANGE,
          "Excuse end date is before its start date",
          "studentId",
          studentId);
    }
    if (reason == null || reason.isBlank()) {
      throw Rejections.reject(
          log,
          ErrorCode.REASON_REQUIRED,
          "Excuse rejected: no reason given",
          "studentId",
          studentId);
    }
    return new Excuse(
        null,
        null,
        studentId,
        branchId,
        fromDate,
        toDate,
        reason.strip(),
        ExcuseStatus.PENDING,
        requestedBy,
        null,
        null,
        null);
  }

  public void approve(UUID admin, Instant at, String note) {
    decide(ExcuseStatus.APPROVED, admin, at, note);
  }

  public void reject(UUID admin, Instant at, String note) {
    decide(ExcuseStatus.REJECTED, admin, at, note);
  }

  /** Whether an approved excuse covers {@code date}. */
  public boolean covers(LocalDate date) {
    return status == ExcuseStatus.APPROVED && !date.isBefore(fromDate) && !date.isAfter(toDate);
  }

  private void decide(ExcuseStatus decision, UUID admin, Instant at, String note) {
    if (status != ExcuseStatus.PENDING) {
      throw Rejections.reject(
          log,
          ErrorCode.EXCUSE_ALREADY_DECIDED,
          "Excuse decision rejected: it was already decided",
          "excuseId",
          id,
          "status",
          status);
    }
    this.status = decision;
    this.decidedBy = admin;
    this.decidedAt = at;
    this.decisionNote = note == null || note.isBlank() ? null : note.strip();
  }

  @Override
  public String toString() {
    return "Excuse[id=" + id + "]";
  }
}
