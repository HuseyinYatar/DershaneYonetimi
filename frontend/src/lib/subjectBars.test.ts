import { describe, expect, it } from "vitest";
import { subjectBars } from "./subjectBars";

const subject = (subjectId: string, net: string, correct: number) => ({
  subjectId,
  subjectName: subjectId,
  correct,
  wrong: 0,
  blank: 40 - correct,
  net,
});
const detail = {
  mockExamId: "m2",
  subjects: [subject("tr", "31.00", 31), subject("mat", "22.00", 22), subject("fen", "10.00", 10)],
} as unknown as Parameters<typeof subjectBars>[0];
const point = (mockExamId: string, nets: Record<string, string>) => ({
  mockExamId,
  examName: mockExamId,
  examDate: "2026-11-01",
  net: "0",
  subjects: Object.entries(nets).map(([subjectId, net]) => ({ subjectId, net })),
});

describe("subjectBars", () => {
  it("makes the strongest bar ink and the most improved of the others violet with a chip", () => {
    const { bars, max } = subjectBars(detail, [
      point("m1", { tr: "20", mat: "20", fen: "6" }),
      point("m2", { tr: "31", mat: "22", fen: "10" }),
    ]);
    expect(bars.map((b) => [b.label, !!b.strong, !!b.accent])).toEqual([
      ["tr", true, false],
      ["mat", false, false],
      ["fen", false, true],
    ]);
    expect(bars[2].chip).toBe("+%67");
    expect(max).toBe(40);
  });

  it("shows no chip without a previous exam", () => {
    const { bars } = subjectBars(detail, [point("m2", { tr: "31", mat: "22", fen: "10" })]);
    expect(bars.some((b) => b.chip)).toBe(false);
    expect(bars[0].strong).toBe(true);
  });
});
