import { describe, expect, it } from "vitest";
import { netDelta, toLinePoints } from "./progress";

const point = (examName: string, examDate: string, net: string) => ({
  mockExamId: examName,
  examName,
  examDate,
  net,
  subjects: [],
});

describe("progress helpers", () => {
  it("maps points to chart points with month ticks", () => {
    expect(toLinePoints([point("Deneme 1", "2026-09-20", "54.50")])).toEqual([
      { value: 54.5, tick: "Eyl", name: "Deneme 1" },
    ]);
  });

  it("computes the delta of the last two exams", () => {
    const series = [
      point("a", "2026-09-01", "50"),
      point("b", "2026-10-01", "68.25"),
      point("c", "2026-11-01", "72.5"),
    ];
    expect(netDelta(series)).toBeCloseTo(4.25);
    expect(netDelta(series.slice(0, 1))).toBeNull();
  });
});
