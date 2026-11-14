import type { components } from "@/api/schema/task";
import { dateKey } from "./week";

export type Assignment = components["schemas"]["AssignmentSummaryResponse"];

const due = (a: Assignment) => (a.dueAt ? new Date(a.dueAt) : null);

export function countByStatus(list: Assignment[]) {
  const count = (...statuses: Assignment["status"][]) =>
    list.filter((a) => statuses.includes(a.status)).length;
  return {
    waiting: count("ASSIGNED"),
    overdue: count("OVERDUE"),
    done: count("SUBMITTED", "GRADED"),
  };
}

// Dashboard list: the most recent overdue ones first, then upcoming deadlines soonest first.
export function upcomingOrOverdue(list: Assignment[], limit = 4, maxOverdue = 2): Assignment[] {
  const byDue = (a: Assignment, b: Assignment) => (a.dueAt ?? "").localeCompare(b.dueAt ?? "");
  const overdue = list
    .filter((a) => a.status === "OVERDUE")
    .sort((a, b) => byDue(b, a)) // newest due date first
    .slice(0, maxOverdue)
    .sort(byDue);
  const waiting = list.filter((a) => a.status === "ASSIGNED").sort(byDue);
  return [...overdue, ...waiting].slice(0, limit);
}

// Deadlines of the given days (local dates), cancelled ones excluded, soonest first.
export function deadlinesOn(list: Assignment[], days: Date[]): Assignment[] {
  const keys = new Set(days.map(dateKey));
  return list
    .filter((a) => a.status !== "CANCELLED" && due(a) && keys.has(dateKey(due(a)!)))
    .sort((a, b) => (a.dueAt ?? "").localeCompare(b.dueAt ?? ""));
}

export function deadlineDayKeys(list: Assignment[], days: Date[]): Set<string> {
  return new Set(deadlinesOn(list, days).map((a) => dateKey(due(a)!)));
}
