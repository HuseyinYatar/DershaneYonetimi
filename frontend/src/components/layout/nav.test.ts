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

  it("gives teachers their own overview, four tabs and no notifications", () => {
    const items = navForRoles(["teacher"], "t-1");
    expect(items.map((item) => [item.label, item.to, item.tab])).toEqual([
      ["Özet", "/", true],
      ["Yoklama", "/sessions", true],
      ["Görevler", "/given-tasks", true],
      ["Sınıflarım", "/classrooms", true],
      ["Denemeler", "/exams", false],
      ["Mesajlar", "/messages", false],
    ]);
    expect(items.some((item) => item.badge)).toBe(false);
  });

  it("gives admins the management pages, four of them in the tab bar", () => {
    const items = navForRoles(["admin"], "a-1");
    expect(items.map((item) => [item.label, item.to, item.tab])).toEqual([
      ["Kullanıcılar", "/users", true],
      ["Sınıflar", "/classrooms", true],
      ["Yoklama", "/sessions", true],
      ["Denemeler", "/exams", true],
      ["Haftalık program", "/schedules", false],
      ["Mazeretler", "/excuses", false],
    ]);
    expect(navForRoles(["admin", "super-admin"], "a-1")).toEqual(items);
  });

  it("keeps the task pages for an admin who also teaches", () => {
    const items = navForRoles(["admin", "teacher"], "a-1");
    expect(items.at(-1)).toMatchObject({ label: "Görevler", to: "/given-tasks", tab: false });
  });

  it("returns nothing for roles without screens yet", () => {
    expect(navForRoles(["guardian"], "g-1")).toEqual([]);
    expect(navForRoles([], "x")).toEqual([]);
  });
});
