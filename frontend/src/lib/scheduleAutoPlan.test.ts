import { describe, expect, it } from "vitest";
import { type Lesson, planWeek } from "./scheduleAutoPlan";
const SLOT_COUNT = 12;

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
    const plan = planWeek([geometry("a"), geometry("b"), geometry("c")], [], DAYS, SLOT_COUNT);

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
    const plan = planWeek([geometry("a")], existing, DAYS, SLOT_COUNT);

    expectNoClash([...existing, ...plan.lessons]);
    expect(plan.lessons.every((l) => l.span.first > 5)).toBe(true);
  });

  it("reports the hours that cannot fit", () => {
    const tooMany = { ...geometry("a"), hours: SLOT_COUNT + 3 };
    const plan = planWeek([tooMany], [], ["MONDAY"], SLOT_COUNT);

    expect(plan.lessons.reduce((n, l) => n + hours(l), 0)).toBe(SLOT_COUNT);
    expect(plan.missing).toEqual([{ ...tooMany, missing: 3 }]);
  });

  it("ignores demands with no hours", () => {
    expect(planWeek([{ ...geometry("a"), hours: 0 }], [], DAYS, SLOT_COUNT)).toEqual({
      lessons: [],
      missing: [],
    });
  });
});

describe("planWeek with a lunch break", () => {
  const demand = { classroomId: "a", teacherId: "t", subjectId: "s", hours: 4 };

  it("never places a lesson on the lunch slot, even inside a two-hour block", () => {
    const plan = planWeek([demand], [], DAYS, SLOT_COUNT, 2, 3);

    expect(plan.missing).toEqual([]);
    expect(plan.lessons.some((l) => l.span.first <= 3 && 3 <= l.span.last)).toBe(false);
  });

  it("reports the hours that only fit on the lunch slot", () => {
    const plan = planWeek([{ ...demand, hours: 3 }], [], ["MONDAY"], 3, 2, 1);

    expect(plan.lessons.reduce((n, l) => n + hours(l), 0)).toBe(2);
    expect(plan.missing).toEqual([{ ...demand, hours: 3, missing: 1 }]);
  });
});
