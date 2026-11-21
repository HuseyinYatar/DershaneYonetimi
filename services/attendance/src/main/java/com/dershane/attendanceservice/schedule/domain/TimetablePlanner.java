package com.dershane.attendanceservice.schedule.domain;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Greedy weekly distribution. A subject goes in blocks of up to {@value #MAX_BLOCK} hours, each
 * block on a different day when possible; no classroom and no teacher is ever in two places at
 * once. Teachers with the most hours are placed first (they are the hardest to fit). The blocked
 * slot (the lunch break) is never used by a lesson, on any day.
 *
 * <p>shortcut: greedy, not optimal; a tight timetable may leave hours in {@code missing}, which the
 * admin fixes by hand. Upgrade to a constraint solver if that becomes common.
 */
public final class TimetablePlanner {

  static final int MAX_BLOCK = 2;

  /**
   * A classroom's need for a subject: {@code hours} still to place this week, by {@code teacherId}.
   */
  public record Demand(UUID classroomId, UUID teacherId, UUID subjectId, int hours) {}

  public record Placed(
      UUID classroomId, UUID teacherId, UUID subjectId, DayOfWeek dayOfWeek, SlotSpan span) {}

  /** What did not fit: the demand and the hours left over. */
  public record Missing(Demand demand, int hours) {}

  public record Plan(List<Placed> lessons, List<Missing> missing) {}

  private record Spot(Placed lesson, int[] score) {}

  private final List<Placed> all;
  private final List<DayOfWeek> days;
  private final int slotCount;
  private final int blockedSlot;

  private TimetablePlanner(
      List<Placed> existing, List<DayOfWeek> days, int slotCount, int blockedSlot) {
    this.all = new ArrayList<>(existing);
    this.days = days;
    this.slotCount = slotCount;
    this.blockedSlot = blockedSlot;
  }

  /**
   * Places {@code demands} around {@code existing} lessons.
   *
   * @param blockedSlot 0-based hour no lesson may cover, or -1 for none
   */
  public static Plan plan(
      List<Demand> demands,
      List<Placed> existing,
      List<DayOfWeek> days,
      int slotCount,
      int blockedSlot) {
    return new TimetablePlanner(existing, days, slotCount, blockedSlot).run(demands);
  }

  private Plan run(List<Demand> demands) {
    Map<UUID, Integer> teacherHours = new HashMap<>();
    for (Demand d : demands) {
      teacherHours.merge(d.teacherId(), d.hours(), Integer::sum);
    }
    List<Demand> ordered =
        demands.stream()
            .filter(d -> d.hours() > 0)
            .sorted(
                Comparator.<Demand>comparingInt(d -> -teacherHours.get(d.teacherId()))
                    .thenComparingInt(d -> -d.hours())
                    .thenComparing(d -> d.classroomId().toString())
                    .thenComparing(d -> d.subjectId().toString()))
            .toList();

    List<Placed> lessons = new ArrayList<>();
    List<Missing> missing = new ArrayList<>();
    for (Demand d : ordered) {
      int left = d.hours();
      while (left > 0) {
        Optional<Placed> spot = Optional.empty();
        for (int size = Math.min(MAX_BLOCK, left); size >= 1 && spot.isEmpty(); size--) {
          spot = bestSpot(d, size);
        }
        if (spot.isEmpty()) {
          break;
        }
        all.add(spot.get());
        lessons.add(spot.get());
        left -= spot.get().span().size();
      }
      if (left > 0) {
        missing.add(new Missing(d, left));
      }
    }
    return new Plan(lessons, missing);
  }

  /**
   * Lower score is better: spread the subject over days, balance the classroom and the teacher,
   * keep the classroom's day without gaps, then take the earliest hour.
   */
  private Optional<Placed> bestSpot(Demand d, int size) {
    Spot best = null;
    for (DayOfWeek day : days) {
      List<Placed> ofClass =
          all.stream()
              .filter(l -> l.classroomId().equals(d.classroomId()) && l.dayOfWeek() == day)
              .toList();
      int[] load = dayLoad(d, day, ofClass);
      for (int first = 0; first + size <= slotCount; first++) {
        SlotSpan span = new SlotSpan(first, first + size - 1);
        if (isBlocked(span) || !isFree(d, day, span)) {
          continue;
        }
        int[] score = {load[0], load[1], load[2], touches(ofClass, span) ? 0 : 1, first};
        if (best == null || isLess(score, best.score())) {
          best =
              new Spot(new Placed(d.classroomId(), d.teacherId(), d.subjectId(), day, span), score);
        }
      }
    }
    return Optional.ofNullable(best).map(Spot::lesson);
  }

  /**
   * The subject's lessons in the classroom that day, the classroom's hours and the teacher's hours.
   */
  private int[] dayLoad(Demand d, DayOfWeek day, List<Placed> ofClass) {
    int sameSubject =
        (int) ofClass.stream().filter(l -> l.subjectId().equals(d.subjectId())).count();
    int classLoad = ofClass.stream().mapToInt(l -> l.span().size()).sum();
    int teacherLoad =
        all.stream()
            .filter(l -> l.teacherId().equals(d.teacherId()) && l.dayOfWeek() == day)
            .mapToInt(l -> l.span().size())
            .sum();
    return new int[] {sameSubject, classLoad, teacherLoad};
  }

  private boolean isBlocked(SlotSpan span) {
    return blockedSlot >= 0 && span.contains(blockedSlot);
  }

  /** True when the classroom has nothing that day yet, or the span sits right next to a lesson. */
  private static boolean touches(List<Placed> ofClass, SlotSpan span) {
    return ofClass.isEmpty()
        || ofClass.stream()
            .anyMatch(
                l -> l.span().last() == span.first() - 1 || l.span().first() == span.last() + 1);
  }

  private boolean isFree(Demand d, DayOfWeek day, SlotSpan span) {
    return all.stream()
        .noneMatch(
            l ->
                l.dayOfWeek() == day
                    && l.span().overlaps(span)
                    && (l.classroomId().equals(d.classroomId())
                        || l.teacherId().equals(d.teacherId())));
  }

  private static boolean isLess(int[] a, int[] b) {
    for (int i = 0; i < a.length; i++) {
      if (a[i] != b[i]) {
        return a[i] < b[i];
      }
    }
    return false;
  }
}
