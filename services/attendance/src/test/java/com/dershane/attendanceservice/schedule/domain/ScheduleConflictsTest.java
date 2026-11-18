package com.dershane.attendanceservice.schedule.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.dershane.attendanceservice.schedule.domain.ScheduleConflicts.Kind;
import com.dershane.attendanceservice.schedule.domain.ScheduleConflicts.Lesson;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScheduleConflictsTest {

  private final UUID classroom = UUID.randomUUID();
  private final UUID teacher = UUID.randomUUID();

  private Lesson lesson(UUID classroomId, UUID teacherId, DayOfWeek day, String from, String to) {
    return new Lesson(
        UUID.randomUUID(), classroomId, teacherId, day, LocalTime.parse(from), LocalTime.parse(to));
  }

  @Test
  void reportsAClassroomWithTwoLessonsAtOnce() {
    Lesson candidate = lesson(classroom, teacher, DayOfWeek.MONDAY, "09:10", "10:00");
    Lesson other = lesson(classroom, UUID.randomUUID(), DayOfWeek.MONDAY, "09:30", "10:20");

    assertThat(ScheduleConflicts.find(candidate, List.of(other))).contains(Kind.CLASSROOM);
  }

  @Test
  void reportsATeacherInTwoClassroomsAtOnce() {
    Lesson candidate = lesson(classroom, teacher, DayOfWeek.MONDAY, "09:10", "10:00");
    Lesson other = lesson(UUID.randomUUID(), teacher, DayOfWeek.MONDAY, "09:10", "10:00");

    assertThat(ScheduleConflicts.find(candidate, List.of(other))).contains(Kind.TEACHER);
  }

  @Test
  void reportsTheClassroomBeforeTheTeacher() {
    Lesson candidate = lesson(classroom, teacher, DayOfWeek.MONDAY, "09:10", "10:00");
    Lesson sameClassroom = lesson(classroom, UUID.randomUUID(), DayOfWeek.MONDAY, "09:10", "10:00");
    Lesson sameTeacher = lesson(UUID.randomUUID(), teacher, DayOfWeek.MONDAY, "09:10", "10:00");

    assertThat(ScheduleConflicts.find(candidate, List.of(sameTeacher, sameClassroom)))
        .contains(Kind.CLASSROOM);
  }

  @Test
  void touchingLessonsOtherDaysAndOtherPeopleDoNotClash() {
    Lesson candidate = lesson(classroom, teacher, DayOfWeek.MONDAY, "09:10", "10:00");
    List<Lesson> others =
        List.of(
            lesson(classroom, teacher, DayOfWeek.MONDAY, "10:00", "10:50"),
            lesson(classroom, teacher, DayOfWeek.TUESDAY, "09:10", "10:00"),
            lesson(UUID.randomUUID(), UUID.randomUUID(), DayOfWeek.MONDAY, "09:10", "10:00"));

    assertThat(ScheduleConflicts.find(candidate, others)).isEmpty();
  }

  @Test
  void aLessonDoesNotClashWithItself() {
    Lesson candidate = lesson(classroom, teacher, DayOfWeek.MONDAY, "09:10", "10:00");

    assertThat(ScheduleConflicts.find(candidate, List.of(candidate))).isEmpty();
  }
}
