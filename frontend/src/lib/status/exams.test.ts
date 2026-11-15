import { describe, expect, it } from "vitest";
import { examBadge } from "./exams";

describe("examBadge", () => {
  it.each([
    ["DRAFT", "neutral", "Taslak"],
    ["KEY_READY", "info", "Cevap anahtarı hazır"],
    ["PROCESSED", "warning", "Açıklanmadı"],
    ["PUBLISHED", "success", "Açıklandı"],
  ] as const)("%s -> %s %s", (status, tone, label) => {
    expect(examBadge(status)).toEqual({ tone, label });
  });
});
