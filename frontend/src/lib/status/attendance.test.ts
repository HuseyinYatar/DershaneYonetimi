import { describe, expect, it } from "vitest";
import { attendanceBadge, excuseBadge } from "./attendance";

describe("attendanceBadge", () => {
  it.each([
    ["PRESENT", "success", "Var"],
    ["ABSENT", "danger", "Yok"],
    ["LATE", "warning", "Geç"],
    ["EXCUSED", "info", "İzinli"],
  ] as const)("%s -> %s %s", (status, tone, label) => {
    expect(attendanceBadge(status)).toEqual({ tone, label });
  });
});

describe("excuseBadge", () => {
  it.each([
    ["PENDING", "warning", "Bekliyor"],
    ["APPROVED", "success", "Onaylandı"],
    ["REJECTED", "danger", "Reddedildi"],
  ] as const)("%s -> %s %s", (status, tone, label) => {
    expect(excuseBadge(status)).toEqual({ tone, label });
  });
});
