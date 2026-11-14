import { describe, expect, it } from "vitest";
import { navForRoles } from "./nav";

describe("navForRoles", () => {
  it("gives students their own pages scoped to their id", () => {
    const items = navForRoles(["student"], "s-1");
    expect(items.map((item) => item.label)).toEqual([
      "Özet",
      "Denemeler",
      "Görevlerim",
      "Yoklama",
      "Mesajlar",
      "Bildirimler",
    ]);
    expect(items[0].to).toBe("/students/s-1");
    expect(items[1].to).toBe("/students/s-1/results");
  });

  it("shows four items in the phone tab bar, the rest under Daha fazla", () => {
    const items = navForRoles(["student"], "s-1");
    expect(items.filter((item) => item.tab).map((item) => item.label)).toEqual([
      "Özet",
      "Denemeler",
      "Görevlerim",
      "Yoklama",
    ]);
  });

  it("returns nothing for roles without screens yet", () => {
    expect(navForRoles(["teacher"], "t-1")).toEqual([]);
    expect(navForRoles(["admin", "super-admin"], "a-1")).toEqual([]);
    expect(navForRoles([], "x")).toEqual([]);
  });
});
