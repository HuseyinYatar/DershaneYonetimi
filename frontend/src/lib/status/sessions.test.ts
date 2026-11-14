import { describe, expect, it } from "vitest";
import { sessionBadge } from "./sessions";

describe("sessionBadge", () => {
  it.each([
    ["PLANNED", "warning", "Yoklama bekliyor"],
    ["COMPLETED", "success", "Yoklama alındı"],
    ["CANCELLED", "neutral", "İptal edildi"],
    ["EXPIRED", "danger", "Yoklama alınmadı"],
  ] as const)("%s -> %s %s", (status, tone, label) => {
    expect(sessionBadge(status)).toEqual({ tone, label });
  });
});
