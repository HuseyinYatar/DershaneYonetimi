import type { components } from "@/api/schema/exam-analysis";
import type { LinePoint } from "@/components/charts/LineChart";
import { formatMonthShort, parseDateOnly } from "./format";

type ProgressPoint = components["schemas"]["ProgressPoint"];

export function toLinePoints(points: ProgressPoint[]): LinePoint[] {
  return points.map((p) => ({
    value: Number(p.net),
    tick: formatMonthShort(parseDateOnly(p.examDate)),
    name: p.examName,
  }));
}

// Net difference between the last two exams of the series; null with fewer than two.
export function netDelta(points: ProgressPoint[]): number | null {
  if (points.length < 2) return null;
  return Number(points[points.length - 1].net) - Number(points[points.length - 2].net);
}
