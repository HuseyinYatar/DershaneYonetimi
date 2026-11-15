import { describe, expect, it } from "vitest";
import { gaugeDash, gaugeSegments } from "./gauge";

describe("gaugeDash", () => {
  it("uses the rate as the dash length on a 100-long path", () => {
    expect(gaugeDash(94)).toBe("94 100");
    expect(gaugeDash(100)).toBe("100 100");
  });

  it("draws nothing at 0 so round caps do not leave a dot", () => {
    expect(gaugeDash(0)).toBeNull();
    expect(gaugeDash(-5)).toBeNull();
    expect(gaugeDash(Number.NaN)).toBeNull();
  });

  it("clamps above 100", () => {
    expect(gaugeDash(130)).toBe("100 100");
  });
});

describe("gaugeSegments", () => {
  it("fills the arc in order with gaps between segments", () => {
    const [a, b] = gaugeSegments([50, 50], 4.5, 2) as { start: number; length: number }[];
    expect(a.start).toBe(0);
    expect(a.length).toBeCloseTo(49);
    expect(b.start).toBeCloseTo(51);
    expect(b.start + b.length).toBeCloseTo(100);
  });

  it("keeps a tiny count visible and still ends at the arc end", () => {
    const segments = gaugeSegments([48, 1, 1, 0]);
    expect(segments[3]).toBeNull();
    expect(segments[1]!.length).toBe(4.5);
    const last = segments[2]!;
    expect(last.start + last.length).toBeCloseTo(100);
  });

  it("draws nothing without counts", () => {
    expect(gaugeSegments([0, 0, Number.NaN])).toEqual([null, null, null]);
  });
});
