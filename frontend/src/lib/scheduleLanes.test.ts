import { describe, expect, it } from "vitest";
import { assignLanes } from "./scheduleLanes";

const b = (id: string, first: number, last: number) => ({ id, span: { first, last } });

describe("assignLanes", () => {
  it("needs no extra lane when lessons do not share an hour", () => {
    const { lanes, laneOf } = assignLanes([b("a", 0, 1), b("b", 2, 3)]);

    expect(lanes).toBe(1);
    expect([laneOf.get("a"), laneOf.get("b")]).toEqual([0, 0]);
  });

  it("puts lessons of the same hour in separate lanes and reuses a freed lane", () => {
    const { lanes, laneOf } = assignLanes([b("a", 0, 2), b("b", 1, 1), b("c", 3, 3), b("d", 2, 2)]);

    expect(lanes).toBe(2);
    expect(laneOf.get("a")).toBe(0);
    expect(laneOf.get("b")).toBe(1);
    expect(laneOf.get("d")).toBe(1);
    expect(laneOf.get("c")).toBe(0);
  });

  it("is empty for no lessons", () => {
    expect(assignLanes([]).lanes).toBe(0);
  });
});
