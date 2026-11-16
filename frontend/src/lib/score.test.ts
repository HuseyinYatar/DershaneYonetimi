import { describe, expect, it } from "vitest";
import {
  describeScore,
  NOT_COMPUTED_TEXT,
  scoreExplanation,
  scoreLabel,
  scoreSourceLabel,
  SCORE_PENDING_TEXT,
} from "./score";

const standard = { coefficientYear: null, notComputedReason: null, linkedExamName: null };

describe("score labels", () => {
  it("labels institutional and national estimates", () => {
    expect(scoreSourceLabel("INSTITUTIONAL")).toBe("tahmini (kurum içi)");
    expect(scoreSourceLabel("NATIONAL")).toBe("tahmini (Türkiye geneli)");
  });

  it("never drops the estimate wording, even without a source", () => {
    expect(scoreSourceLabel(null)).toBe("tahmini");
  });

  it("labels a coefficient score with the year of its coefficients", () => {
    expect(scoreLabel({ source: null, coefficientYear: 2026 })).toBe(
      "tahmini (2026 katsayılarıyla)",
    );
    expect(scoreLabel({ source: "INSTITUTIONAL", coefficientYear: null })).toBe(
      "tahmini (kurum içi)",
    );
  });

  it("describes a calculated score with its label", () => {
    expect(
      describeScore({ ...standard, estimatedScore: "391.40", source: "INSTITUTIONAL" }),
    ).toEqual({
      kind: "value",
      label: "tahmini (kurum içi)",
    });
    expect(
      describeScore({ ...standard, estimatedScore: "330.33", source: null, coefficientYear: 2026 }),
    ).toEqual({ kind: "value", label: "tahmini (2026 katsayılarıyla)" });
  });

  it("explains a missing national score instead of showing a number", () => {
    expect(describeScore({ ...standard, estimatedScore: null, source: null })).toEqual({
      kind: "pending",
      text: SCORE_PENDING_TEXT,
    });
    expect(SCORE_PENDING_TEXT).toBe("Türkiye geneli istatistik henüz girilmedi");
  });

  it("explains why a coefficient score was not computed", () => {
    const view = describeScore({
      ...standard,
      estimatedScore: null,
      source: null,
      coefficientYear: 2026,
      notComputedReason: "LINKED_RESULT_MISSING",
    });
    expect(view).toEqual({ kind: "pending", text: NOT_COMPUTED_TEXT.LINKED_RESULT_MISSING });
  });

  it("names the TYT exam an AYT score counted", () => {
    expect(
      scoreExplanation({ source: null, coefficientYear: 2026, linkedExamName: "TYT Deneme 4" }),
    ).toBe("2026 tahmini katsayılarıyla hesaplandı. TYT netleri: TYT Deneme 4.");
    expect(
      scoreExplanation({ source: "INSTITUTIONAL", coefficientYear: null, linkedExamName: null }),
    ).toBe("Kurum içi ortalama ve standart sapmaya göre hesaplandı");
  });
});
