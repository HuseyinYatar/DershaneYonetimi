import { describe, expect, it } from "vitest";
import {
  countByStatus,
  deadlineDayKeys,
  deadlinesOn,
  upcomingOrOverdue,
  type Assignment,
} from "./assignments";
import { weekDays } from "./week";

const a = (id: string, status: Assignment["status"], dueAt: string): Assignment => ({
  id,
  status,
  dueAt,
});
const iso = (day: number, hour = 12) => new Date(2026, 10, day, hour).toISOString();

const list = [
  a("o1", "OVERDUE", iso(2)),
  a("o2", "OVERDUE", iso(11)),
  a("o3", "OVERDUE", iso(10)),
  a("w1", "ASSIGNED", iso(15)),
  a("w2", "ASSIGNED", iso(13)),
  a("s1", "SUBMITTED", iso(12)),
  a("g1", "GRADED", iso(9)),
  a("c1", "CANCELLED", iso(14)),
];

describe("assignment helpers", () => {
  it("counts waiting, overdue and done (submitted or graded)", () => {
    expect(countByStatus(list)).toEqual({ waiting: 2, overdue: 3, done: 2 });
  });

  it("lists the latest overdue first, then upcoming by deadline", () => {
    expect(upcomingOrOverdue(list).map((x) => x.id)).toEqual(["o3", "o2", "w2", "w1"]);
    expect(upcomingOrOverdue(list, 3).map((x) => x.id)).toEqual(["o3", "o2", "w2"]);
  });

  it("picks the deadlines of a week without cancelled ones", () => {
    const week = weekDays(new Date(2026, 10, 13));
    expect(deadlinesOn(list, week).map((x) => x.id)).toEqual(["g1", "o3", "o2", "s1", "w2", "w1"]);
    expect([...deadlineDayKeys(list, week)].sort()).toEqual([
      "2026-11-09",
      "2026-11-10",
      "2026-11-11",
      "2026-11-12",
      "2026-11-13",
      "2026-11-15",
    ]);
  });
});
