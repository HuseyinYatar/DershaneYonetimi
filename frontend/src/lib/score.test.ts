import { describe, expect, it } from "vitest";
import { describeScore, scoreSourceLabel, SCORE_PENDING_TEXT } from "./score";

describe("score labels", () => {
  it("labels institutional and national estimates", () => {
    expect(scoreSourceLabel("INSTITUTIONAL")).toBe("tahmini (kurum içi)");
    expect(scoreSourceLabel("NATIONAL")).toBe("tahmini (Türkiye geneli)");
  });

  it("never drops the estimate wording, even without a source", () => {
    expect(scoreSourceLabel(null)).toBe("tahmini");
  });

  it("describes a calculated score with its label", () => {
    expect(describeScore({ estimatedScore: "391.40", source: "INSTITUTIONAL" })).toEqual({
      kind: "value",
      label: "tahmini (kurum içi)",
    });
  });

  it("explains a missing national score instead of showing a number", () => {
    expect(describeScore({ estimatedScore: null, source: null })).toEqual({
      kind: "pending",
      text: SCORE_PENDING_TEXT,
    });
    expect(SCORE_PENDING_TEXT).toBe("Türkiye geneli istatistik henüz girilmedi");
  });
});
