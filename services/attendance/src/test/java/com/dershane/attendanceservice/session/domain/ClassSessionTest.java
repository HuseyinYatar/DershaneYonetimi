package com.dershane.attendanceservice.session.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.dershane.attendanceservice.shared.domain.DomainException;
import com.dershane.attendanceservice.shared.domain.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClassSessionTest {

  private static final LocalDate DAY = LocalDate.of(2026, 10, 30);
  private static final Duration EARLY = Duration.ofMinutes(15);

  private final UUID teacherId = UUID.randomUUID();

  private ClassSession planned() {
    return ClassSession.plan(
        null,
        UUID.randomUUID(),
        UUID.randomUUID(),
        teacherId,
        UUID.randomUUID(),
        DAY,
        LocalTime.of(9, 0),
        LocalTime.of(9, 40));
  }

  private static LocalDateTime at(LocalDate day, int hour, int minute) {
    return LocalDateTime.of(day, LocalTime.of(hour, minute));
  }

  private static void assertRejected(Runnable action, ErrorCode expected) {
    assertThatExceptionOfType(DomainException.class)
        .isThrownBy(action::run)
        .satisfies(ex -> assertThat(ex.getErrorCode()).isEqualTo(expected));
  }

  @Test
  void endTimeMustBeAfterStartTime() {
    assertRejected(
        () ->
            ClassSession.plan(
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                teacherId,
                UUID.randomUUID(),
                DAY,
                LocalTime.of(9, 40),
                LocalTime.of(9, 40)),
        ErrorCode.INVALID_TIME_RANGE);
  }

  @Test
  void attendanceOpensFifteenMinutesBeforeTheLesson() {
    ClassSession session = planned();

    assertThat(session.checkTakeable(at(DAY, 8, 45), EARLY, false, false))
        .isEqualTo(TakeMode.NORMAL);
    assertRejected(
        () -> session.checkTakeable(at(DAY, 8, 44), EARLY, false, false),
        ErrorCode.ATTENDANCE_TOO_EARLY);
  }

  @Test
  void attendanceStaysOpenUntilTheEndOfTheDay() {
    ClassSession session = planned();

    assertThat(session.checkTakeable(at(DAY, 23, 59), EARLY, false, false))
        .isEqualTo(TakeMode.NORMAL);
  }

  @Test
  void teacherCannotTakeAttendanceAfterTheDayEnded() {
    ClassSession session = planned();

    assertRejected(
        () -> session.checkTakeable(at(DAY.plusDays(1), 0, 1), EARLY, false, true),
        ErrorCode.SESSION_EXPIRED);
  }

  @Test
  void adminTakesLateAttendanceOnlyWithAReason() {
    ClassSession session = planned();
    LocalDateTime nextDay = at(DAY.plusDays(1), 10, 0);

    assertRejected(
        () -> session.checkTakeable(nextDay, EARLY, true, false), ErrorCode.REASON_REQUIRED);
    assertThat(session.checkTakeable(nextDay, EARLY, true, true)).isEqualTo(TakeMode.LATE_BY_ADMIN);
  }

  @Test
  void expiredSessionFollowsTheSameRulesEvenOnTheSessionDay() {
    ClassSession session = planned();
    session.expire(DAY.plusDays(1));

    assertThat(session.getStatus()).isEqualTo(SessionStatus.EXPIRED);
    assertRejected(
        () -> session.checkTakeable(at(DAY, 10, 0), EARLY, false, true), ErrorCode.SESSION_EXPIRED);
    assertThat(session.checkTakeable(at(DAY, 10, 0), EARLY, true, true))
        .isEqualTo(TakeMode.LATE_BY_ADMIN);
  }

  @Test
  void completedAndCancelledSessionsTakeNoAttendance() {
    ClassSession completed = planned();
    completed.complete(teacherId, Instant.now());
    ClassSession cancelled = planned();
    cancelled.cancel();

    assertRejected(
        () -> completed.checkTakeable(at(DAY, 10, 0), EARLY, true, true),
        ErrorCode.ATTENDANCE_ALREADY_TAKEN);
    assertRejected(
        () -> cancelled.checkTakeable(at(DAY, 10, 0), EARLY, true, true),
        ErrorCode.SESSION_CANCELLED);
  }

  @Test
  void onlyAPlannedSessionCanBeCancelledOrGetASubstitute() {
    ClassSession session = planned();
    session.complete(teacherId, Instant.now());

    assertRejected(session::cancel, ErrorCode.SESSION_NOT_PLANNED);
    assertRejected(() -> session.reassignTeacher(UUID.randomUUID()), ErrorCode.SESSION_NOT_PLANNED);
  }

  @Test
  void substituteReplacesTheTeacher() {
    ClassSession session = planned();
    UUID substitute = UUID.randomUUID();

    session.reassignTeacher(substitute);

    assertThat(session.isTeacher(substitute)).isTrue();
    assertThat(session.isTeacher(teacherId)).isFalse();
  }

  @Test
  void aSessionCompletesOnceFromPlannedOrExpired() {
    ClassSession expired = planned();
    expired.expire(DAY.plusDays(1));
    expired.complete(teacherId, Instant.parse("2026-10-31T08:00:00Z"));

    assertThat(expired.getStatus()).isEqualTo(SessionStatus.COMPLETED);
    assertThat(expired.getTakenBy()).isEqualTo(teacherId);
    assertRejected(
        () -> expired.complete(teacherId, Instant.now()), ErrorCode.ATTENDANCE_ALREADY_TAKEN);
  }

  @Test
  void onlyAPlannedSessionOfAPastDayExpires() {
    ClassSession today = planned();
    ClassSession past = planned();
    ClassSession cancelled = planned();
    cancelled.cancel();

    assertThat(today.expire(DAY)).isFalse();
    assertThat(past.expire(DAY.plusDays(1))).isTrue();
    assertThat(cancelled.expire(DAY.plusDays(1))).isFalse();
    assertThat(cancelled.getStatus()).isEqualTo(SessionStatus.CANCELLED);
  }

  @Test
  void correctionsNeedACompletedSession() {
    ClassSession session = planned();

    assertRejected(() -> session.requireCompleted("test"), ErrorCode.SESSION_NOT_COMPLETED);
    session.complete(teacherId, Instant.now());
    session.requireCompleted("test");
  }
}
