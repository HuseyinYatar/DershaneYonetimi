package com.dershane.attendanceservice.schedule.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.dershane.attendanceservice.schedule.domain.TimetablePlanner.Demand;
import com.dershane.attendanceservice.schedule.domain.TimetablePlanner.Missing;
import com.dershane.attendanceservice.schedule.domain.TimetablePlanner.Placed;
import com.dershane.attendanceservice.schedule.domain.TimetablePlanner.Plan;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TimetablePlannerTest {

  private static final int SLOT_COUNT = 12;
  private static final List<DayOfWeek> DAYS =
      List.of(
          DayOfWeek.MONDAY,
          DayOfWeek.TUESDAY,
          DayOfWeek.WEDNESDAY,
          DayOfWeek.THURSDAY,
          DayOfWeek.FRIDAY);

  private final UUID geometry = UUID.randomUUID();
  private final UUID geometryTeacher = UUID.randomUUID();

  private Demand demand(UUID classroom, int hours) {
    return new Demand(classroom, geometryTeacher, geometry, hours);
  }

  private static int hours(Placed lesson) {
    return lesson.span().size();
  }

  /** Nobody (classroom or teacher) is in two places at once. */
  private static void assertNoClash(List<Placed> lessons) {
    for (int i = 0; i < lessons.size(); i++) {
      for (int j = i + 1; j < lessons.size(); j++) {
        Placed a = lessons.get(i);
        Placed b = lessons.get(j);
        boolean together = a.dayOfWeek() == b.dayOfWeek() && a.span().overlaps(b.span());
        assertThat(together && a.classroomId().equals(b.classroomId()))
            .as("classroom %s on %s", a.classroomId(), a.dayOfWeek())
            .isFalse();
        assertThat(together && a.teacherId().equals(b.teacherId()))
            .as("teacher %s on %s", a.teacherId(), a.dayOfWeek())
            .isFalse();
      }
    }
  }

  @Test
  void placesOneTeachersFourHoursInEachOfThreeClassroomsWithoutClashes() {
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    UUID c = UUID.randomUUID();

    Plan plan =
        TimetablePlanner.plan(
            List.of(demand(a, 4), demand(b, 4), demand(c, 4)), List.of(), DAYS, SLOT_COUNT, -1);

    assertThat(plan.missing()).isEmpty();
    assertNoClash(plan.lessons());
    for (UUID classroom : List.of(a, b, c)) {
      List<Placed> own =
          plan.lessons().stream().filter(l -> l.classroomId().equals(classroom)).toList();
      assertThat(own.stream().mapToInt(TimetablePlannerTest::hours).sum()).isEqualTo(4);
      assertThat(Set.copyOf(own.stream().map(Placed::dayOfWeek).toList()).size()).isGreaterThan(1);
    }
  }

  @Test
  void keepsAwayFromLessonsThatAlreadyExist() {
    List<Placed> existing =
        DAYS.stream()
            .map(
                day ->
                    new Placed(
                        UUID.randomUUID(),
                        geometryTeacher,
                        UUID.randomUUID(),
                        day,
                        new SlotSpan(0, 5)))
            .toList();

    Plan plan =
        TimetablePlanner.plan(
            List.of(demand(UUID.randomUUID(), 4)), existing, DAYS, SLOT_COUNT, -1);

    List<Placed> all = new java.util.ArrayList<>(existing);
    all.addAll(plan.lessons());
    assertNoClash(all);
    assertThat(plan.lessons()).allMatch(l -> l.span().first() > 5);
  }

  @Test
  void reportsTheHoursThatCannotFit() {
    Demand tooMany = demand(UUID.randomUUID(), SLOT_COUNT + 3);

    Plan plan =
        TimetablePlanner.plan(
            List.of(tooMany), List.of(), List.of(DayOfWeek.MONDAY), SLOT_COUNT, -1);

    assertThat(plan.lessons().stream().mapToInt(TimetablePlannerTest::hours).sum())
        .isEqualTo(SLOT_COUNT);
    assertThat(plan.missing()).containsExactly(new Missing(tooMany, 3));
  }

  @Test
  void ignoresDemandsWithNoHours() {
    Plan plan =
        TimetablePlanner.plan(
            List.of(demand(UUID.randomUUID(), 0)), List.of(), DAYS, SLOT_COUNT, -1);

    assertThat(plan.lessons()).isEmpty();
    assertThat(plan.missing()).isEmpty();
  }

  @Test
  void placesTheTeacherWithTheMostHoursFirst() {
    UUID busyTeacher = UUID.randomUUID();
    UUID classroom = UUID.randomUUID();
    Demand light = new Demand(classroom, geometryTeacher, geometry, 1);
    Demand heavy = new Demand(UUID.randomUUID(), busyTeacher, UUID.randomUUID(), 3);

    Plan plan =
        TimetablePlanner.plan(
            List.of(light, heavy), List.of(), List.of(DayOfWeek.MONDAY), SLOT_COUNT, -1);

    assertThat(plan.lessons().get(0).teacherId()).isEqualTo(busyTeacher);
  }

  @Test
  void neverPlacesALessonOnTheLunchSlotEvenInsideATwoHourBlock() {
    UUID classroom = UUID.randomUUID();

    Plan plan =
        TimetablePlanner.plan(List.of(demand(classroom, 4)), List.of(), DAYS, SLOT_COUNT, 3);

    assertThat(plan.missing()).isEmpty();
    assertThat(plan.lessons()).noneMatch(l -> l.span().contains(3));
  }

  @Test
  void reportsTheHoursThatOnlyFitOnTheLunchSlot() {
    Demand demand = demand(UUID.randomUUID(), 3);

    Plan plan = TimetablePlanner.plan(List.of(demand), List.of(), List.of(DayOfWeek.MONDAY), 3, 1);

    assertThat(plan.lessons().stream().mapToInt(TimetablePlannerTest::hours).sum()).isEqualTo(2);
    assertThat(plan.missing()).containsExactly(new Missing(demand, 1));
  }
}
