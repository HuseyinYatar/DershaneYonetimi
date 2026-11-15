import { describe, expect, it } from "vitest";
import { assignmentBadge, taskActiveBadge } from "./tasks";

describe("assignmentBadge", () => {
  it.each([
    ["ASSIGNED", "warning", "Bekliyor"],
    ["SUBMITTED", "info", "Teslim edildi"],
    ["OVERDUE", "danger", "Gecikti"],
    ["GRADED", "success", "Değerlendirildi"],
    ["CANCELLED", "neutral", "İptal edildi"],
  ] as const)("%s -> %s %s", (status, tone, label) => {
    expect(assignmentBadge(status)).toEqual({ tone, label });
  });
});

describe("taskActiveBadge", () => {
  it("labels active and deactivated tasks", () => {
    expect(taskActiveBadge(true)).toEqual({ tone: "success", label: "Aktif" });
    expect(taskActiveBadge(false)).toEqual({ tone: "neutral", label: "Pasif" });
  });
});
