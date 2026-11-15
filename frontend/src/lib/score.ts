import type { components } from "@/api/schema/exam-analysis";

type Score = components["schemas"]["ScoreRead"];

export const SCORE_PENDING_TEXT = "Türkiye geneli istatistik henüz girilmedi";

// An estimated score is never shown without its source label (see CLAUDE.md, puanlama).
export function scoreSourceLabel(source: Score["source"]): string {
  if (source === "INSTITUTIONAL") return "tahmini (kurum içi)";
  if (source === "NATIONAL") return "tahmini (Türkiye geneli)";
  return "tahmini";
}

export type ScoreView = { kind: "pending"; text: string } | { kind: "value"; label: string };

export function describeScore(score: Pick<Score, "estimatedScore" | "source">): ScoreView {
  if (score.estimatedScore === null) return { kind: "pending", text: SCORE_PENDING_TEXT };
  return { kind: "value", label: scoreSourceLabel(score.source) };
}

export function scoreExplanation(source: Score["source"]): string {
  return source === "NATIONAL"
    ? "Türkiye geneli ortalama ve standart sapmaya göre hesaplandı"
    : "Kurum içi ortalama ve standart sapmaya göre hesaplandı";
}
