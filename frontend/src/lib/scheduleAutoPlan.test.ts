import { describe, expect, it } from "vitest";
import { type Lesson, planWeek } from "./scheduleAutoPlan";
import { SLOT_COUNT } from "./scheduleBoard";

const DAYS = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"];
const hours = (l: Lesson) => l.span.last - l.span.first + 1;

// Nobody (classroom or teacher) is in two places at once.
function expectNoClash(lessons: Lesson[]) {
  for (const key of ["classroomId", "teacherId"] as const) {
    lessons.forEach((a, i) =>
      lessons.slice(i + 1).forEach((b) => {
        const clash =
          a[key] === b[key] &&
          a.dayOfWeek === b.dayOfWeek &&
          a.span.first <= b.span.last &&
          b.span.first <= a.span.last;
        expect(clash, `${key} ${a[key]} ${a.dayOfWeek}`).toBe(false);
      }),
    );
  }
}

describe("planWeek", () => {
  const geometry = (classroomId: string) => ({
    classroomId,
    teacherId: "t-geometry",
    subjectId: "s-geometry",
    hours: 4,
  });

  it("places one teacher's 4 hours in each of 3 classrooms (12 hours) without clashes", () => {
    const plan = planWeek([geometry("a"), geometry("b"), geometry("c")], [], DAYS);

    expect(plan.missing).toEqual([]);
    expectNoClash(plan.lessons);
    for (const c of ["a", "b", "c"]) {
      const own = plan.lessons.filter((l) => l.classroomId === c);
      expect(own.reduce((n, l) => n + hours(l), 0)).toBe(4);
      expect(new Set(own.map((l) => l.dayOfWeek)).size).toBeGreaterThan(1);
    }
  });

  it("keeps away from lessons that already exist", () => {
    const existing: Lesson[] = DAYS.map((dayOfWeek) => ({
      classroomId: "other",
      teacherId: "t-geometry",
      subjectId: "s-x",
      dayOfWeek,
      span: { first: 0, last: 5 },
    }));
    const plan = planWeek([geometry("a")], existing, DAYS);

    expectNoClash([...existing, ...plan.lessons]);
    expect(plan.lessons.every((l) => l.span.first > 5)).toBe(true);
  });

  it("reports the hours that cannot fit", () => {
    const tooMany = { ...geometry("a"), hours: SLOT_COUNT + 3 };
    const plan = planWeek([tooMany], [], ["MONDAY"]);

    expect(plan.lessons.reduce((n, l) => n + hours(l), 0)).toBe(SLOT_COUNT);
    expect(plan.missing).toEqual([{ ...tooMany, missing: 3 }]);
  });

  it("ignores demands with no hours", () => {
    expect(planWeek([{ ...geometry("a"), hours: 0 }], [], DAYS)).toEqual({
      lessons: [],
      missing: [],
    });
  });
});
