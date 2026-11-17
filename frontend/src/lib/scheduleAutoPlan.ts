import { SLOT_COUNT, type SlotSpan } from "./scheduleBoard";

// A classroom's need for a subject: `hours` lesson hours still to place this week, taught by `teacherId`.
export interface Demand {
  classroomId: string;
  teacherId: string;
  subjectId: string;
  hours: number;
}

export interface Lesson {
  classroomId: string;
  teacherId: string;
  subjectId: string;
  dayOfWeek: string;
  span: SlotSpan;
}

export interface Plan {
  lessons: Lesson[];
  /** Demands that did not fully fit, with the hours left over. */
  missing: (Demand & { missing: number })[];
}

const clash = (a: SlotSpan, b: SlotSpan) => a.first <= b.last && b.first <= a.last;

/**
 * Greedy weekly distribution. A subject goes in blocks of up to `maxBlock` hours, each block on a
 * different day when possible; no classroom and no teacher is ever in two places at once.
 * Teachers with the most hours are placed first (they are the hardest to fit).
 * shortcut: greedy, not optimal; a tight timetable may leave hours in `missing`, which the admin fixes by hand.
 */
export function planWeek(
  demands: Demand[],
  existing: Lesson[],
  days: string[],
  maxBlock = 2,
): Plan {
  const all = [...existing];
  const lessons: Lesson[] = [];
  const missing: Plan["missing"] = [];

  const teacherHours = new Map<string, number>();
  for (const d of demands) {
    teacherHours.set(d.teacherId, (teacherHours.get(d.teacherId) ?? 0) + d.hours);
  }
  const ordered = demands
    .filter((d) => d.hours > 0)
    .sort(
      (a, b) =>
        teacherHours.get(b.teacherId)! - teacherHours.get(a.teacherId)! ||
        b.hours - a.hours ||
        a.classroomId.localeCompare(b.classroomId) ||
        a.subjectId.localeCompare(b.subjectId),
    );

  const free = (key: "classroomId" | "teacherId", d: Demand, day: string, span: SlotSpan) =>
    !all.some((l) => l[key] === d[key] && l.dayOfWeek === day && clash(l.span, span));

  // Lower is better: spread the subject over days, balance the classroom and the teacher,
  // keep the classroom's day without gaps, then take the earliest hour.
  function bestSpot(d: Demand, size: number): Lesson | undefined {
    let best: { lesson: Lesson; score: number[] } | undefined;
    for (const day of days) {
      const ofClass = all.filter((l) => l.classroomId === d.classroomId && l.dayOfWeek === day);
      const sameSubject = ofClass.filter((l) => l.subjectId === d.subjectId).length;
      const classLoad = ofClass.reduce((n, l) => n + l.span.last - l.span.first + 1, 0);
      const teacherLoad = all
        .filter((l) => l.teacherId === d.teacherId && l.dayOfWeek === day)
        .reduce((n, l) => n + l.span.last - l.span.first + 1, 0);
      for (let first = 0; first + size <= SLOT_COUNT; first++) {
        const span = { first, last: first + size - 1 };
        if (!free("classroomId", d, day, span) || !free("teacherId", d, day, span)) continue;
        const touching =
          ofClass.length === 0 ||
          ofClass.some((l) => l.span.last === first - 1 || l.span.first === span.last + 1);
        const score = [sameSubject, classLoad, teacherLoad, touching ? 0 : 1, first];
        if (!best || isLess(score, best.score)) {
          best = { lesson: { ...pick(d), dayOfWeek: day, span }, score };
        }
      }
    }
    return best?.lesson;
  }

  for (const d of ordered) {
    let left = d.hours;
    while (left > 0) {
      let spot: Lesson | undefined;
      for (let size = Math.min(maxBlock, left); size >= 1 && !spot; size--) {
        spot = bestSpot(d, size);
      }
      if (!spot) break;
      all.push(spot);
      lessons.push(spot);
      left -= spot.span.last - spot.span.first + 1;
    }
    if (left > 0) missing.push({ ...d, missing: left });
  }
  return { lessons, missing };
}

const pick = (d: Demand) => ({
  classroomId: d.classroomId,
  teacherId: d.teacherId,
  subjectId: d.subjectId,
});

function isLess(a: number[], b: number[]): boolean {
  for (let i = 0; i < a.length; i++) {
    if (a[i] !== b[i]) return a[i] < b[i];
  }
  return false;
}
