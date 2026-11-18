package com.dershane.attendanceservice.schedule.domain;

import com.dershane.attendanceservice.shared.domain.ErrorCode;
import com.dershane.attendanceservice.shared.domain.Rejections;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * The automatic distribution proposed for a branch, kept on the server until an admin saves it as
 * real schedules or throws it away. A branch has at most one. Its lessons are not schedules yet:
 * they are checked against the grid, the lunch break and the existing timetable each time they
 * change and once more when saved.
 */
@Slf4j
@Getter
public class TimetableDraft {

  /** One proposed weekly lesson. */
  public record DraftLesson(
      UUID id,
      UUID classroomId,
      UUID teacherId,
      UUID subjectId,
      DayOfWeek dayOfWeek,
      LocalTime startsAt,
      LocalTime endsAt) {

    ScheduleConflicts.Lesson asLesson() {
      return new ScheduleConflicts.Lesson(id, classroomId, teacherId, dayOfWeek, startsAt, endsAt);
    }
  }

  /** Hours of a demand the planner could not place. */
  public record MissingHours(UUID classroomId, UUID teacherId, UUID subjectId, int hours) {}

  private final UUID id;
  private final UUID branchId;

  /** Set by the database on the first save; null before. */
  private final Instant createdAt;

  private final List<DraftLesson> lessons;
  private final List<MissingHours> missing;

  /** Rehydrates a saved draft. Use {@link #create} for a new one. */
  public TimetableDraft(
      UUID id,
      UUID branchId,
      Instant createdAt,
      List<DraftLesson> lessons,
      List<MissingHours> missing) {
    this.id = id;
    this.branchId = branchId;
    this.createdAt = createdAt;
    this.lessons = new ArrayList<>(lessons);
    this.missing = List.copyOf(missing);
  }

  public static TimetableDraft create(
      UUID branchId, List<DraftLesson> lessons, List<MissingHours> missing) {
    return new TimetableDraft(UUID.randomUUID(), branchId, null, lessons, missing);
  }

  public List<DraftLesson> getLessons() {
    return List.copyOf(lessons);
  }

  public DraftLesson requireLesson(UUID lessonId) {
    return lessons.stream()
        .filter(l -> l.id().equals(lessonId))
        .findFirst()
        .orElseThrow(
            () ->
                Rejections.reject(
                    log,
                    ErrorCode.DRAFT_LESSON_NOT_FOUND,
                    "Draft lesson not found",
                    "draftId",
                    id,
                    "lessonId",
                    lessonId));
  }

  public void replaceLesson(DraftLesson updated) {
    lessons.replaceAll(l -> l.id().equals(updated.id()) ? updated : l);
  }

  public void removeLesson(UUID lessonId) {
    requireLesson(lessonId);
    lessons.removeIf(l -> l.id().equals(lessonId));
  }

  /**
   * Rejects a lesson that does not line up with the grid, sits on the lunch hour, or clashes with
   * an existing lesson or another lesson of this draft.
   */
  public void requireValid(
      DraftLesson lesson, TimetableGrid grid, Collection<ScheduleConflicts.Lesson> existing) {
    SlotSpan span =
        grid.exactly(lesson.startsAt(), lesson.endsAt())
            .orElseThrow(
                () -> rejected(ErrorCode.LESSON_OUTSIDE_GRID, "Lesson is off the grid", lesson));
    if (grid.coversLunch(span)) {
      throw rejected(ErrorCode.LUNCH_BREAK_SLOT, "Lesson covers the lunch hour", lesson);
    }
    List<ScheduleConflicts.Lesson> others = new ArrayList<>(existing);
    lessons.stream()
        .filter(l -> !l.id().equals(lesson.id()))
        .map(DraftLesson::asLesson)
        .forEach(others::add);
    Optional<ScheduleConflicts.Kind> clash = ScheduleConflicts.find(lesson.asLesson(), others);
    if (clash.isPresent()) {
      boolean classroom = clash.get() == ScheduleConflicts.Kind.CLASSROOM;
      throw rejected(
          classroom ? ErrorCode.CLASSROOM_BUSY : ErrorCode.TEACHER_BUSY,
          classroom ? "Classroom already has a lesson then" : "Teacher already has a lesson then",
          lesson);
    }
  }

  private RuntimeException rejected(ErrorCode code, String message, DraftLesson lesson) {
    return Rejections.reject(
        log,
        code,
        message,
        "draftId",
        id,
        "lessonId",
        lesson.id(),
        "classroomId",
        lesson.classroomId(),
        "teacherId",
        lesson.teacherId());
  }
}
