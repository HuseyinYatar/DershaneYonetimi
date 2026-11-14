import { describe, expect, it } from "vitest";
import { assignmentBadge } from "./tasks";

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
