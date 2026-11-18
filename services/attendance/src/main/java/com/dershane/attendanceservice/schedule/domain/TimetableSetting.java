package com.dershane.attendanceservice.schedule.domain;

import com.dershane.attendanceservice.shared.domain.ErrorCode;
import com.dershane.attendanceservice.shared.domain.Rejections;
import java.time.LocalTime;
import java.util.UUID;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * The lesson-hour grid of a branch: when the first lesson starts, how long a lesson and a break
 * are, how many lessons a day has and which lesson hour (if any) is the lunch break. It only helps
 * the timetable screen lay out rows; the times of a {@link ClassSchedule} stay free and are not
 * rewritten when the grid changes.
 */
@Slf4j
@Getter
public class TimetableSetting {

  static final LocalTime EARLIEST_START = LocalTime.of(5, 0);
  static final LocalTime LATEST_START = LocalTime.of(20, 0);
  static final int MIN_LESSON_MINUTES = 20;
  static final int MAX_LESSON_MINUTES = 120;
  static final int MAX_BREAK_MINUTES = 60;
  static final int MAX_LESSON_COUNT = 16;
  private static final int LAST_MINUTE_OF_DAY = 23 * 60 + 59;

  private final UUID branchId;

  /** Version the setting was loaded with; null until saved. Used for optimistic locking. */
  private final Long version;

  private final LocalTime firstLessonStart;
  private final int lessonMinutes;
  private final int breakMinutes;
  private final int lessonCount;

  /** The lesson hour (1-based) that is the lunch break, kept busy on every day; 0 means none. */
  private final int lunchLesson;

  /** Rehydrates a saved setting. Use {@link #of} for a new or changed one. */
  public TimetableSetting(
      UUID branchId,
      Long version,
      LocalTime firstLessonStart,
      int lessonMinutes,
      int breakMinutes,
      int lessonCount,
      int lunchLesson) {
    this.branchId = branchId;
    this.version = version;
    this.firstLessonStart = firstLessonStart;
    this.lessonMinutes = lessonMinutes;
    this.breakMinutes = breakMinutes;
    this.lessonCount = lessonCount;
    this.lunchLesson = lunchLesson;
  }

  /** What a branch gets until it customizes the grid: 08:10, 50-minute lessons, 12 a day. */
  public static TimetableSetting defaults(UUID branchId) {
    return new TimetableSetting(branchId, null, LocalTime.of(8, 10), 50, 10, 12, 0);
  }

  /** Validates and builds a setting; {@code version} is the loaded one, or null for a new row. */
  public static TimetableSetting of(
      UUID branchId,
      Long version,
      LocalTime firstLessonStart,
      int lessonMinutes,
      int breakMinutes,
      int lessonCount,
      int lunchLesson) {
    requireValidGrid(branchId, firstLessonStart, lessonMinutes, breakMinutes, lessonCount);
    if (lunchLesson < 0 || lunchLesson > lessonCount) {
      throw invalid(branchId, "lunch lesson out of range");
    }
    int lastEnd =
        firstLessonStart.getHour() * 60
            + firstLessonStart.getMinute()
            + lessonCount * lessonMinutes
            + (lessonCount - 1) * breakMinutes;
    if (lastEnd > LAST_MINUTE_OF_DAY) {
      throw invalid(branchId, "last lesson ends after the day");
    }
    return new TimetableSetting(
        branchId, version, firstLessonStart, lessonMinutes, breakMinutes, lessonCount, lunchLesson);
  }

  private static void requireValidGrid(
      UUID branchId,
      LocalTime firstLessonStart,
      int lessonMinutes,
      int breakMinutes,
      int lessonCount) {
    if (firstLessonStart.isBefore(EARLIEST_START) || firstLessonStart.isAfter(LATEST_START)) {
      throw invalid(branchId, "first lesson start out of range");
    }
    if (lessonMinutes < MIN_LESSON_MINUTES || lessonMinutes > MAX_LESSON_MINUTES) {
      throw invalid(branchId, "lesson length out of range");
    }
    if (breakMinutes < 0 || breakMinutes > MAX_BREAK_MINUTES) {
      throw invalid(branchId, "break length out of range");
    }
    if (lessonCount < 1 || lessonCount > MAX_LESSON_COUNT) {
      throw invalid(branchId, "lesson count out of range");
    }
  }

  private static RuntimeException invalid(UUID branchId, String rule) {
    return Rejections.reject(
        log,
        ErrorCode.TIMETABLE_SETTING_INVALID,
        "Timetable setting rejected: " + rule,
        "branchId",
        branchId);
  }
}
