import type { components } from "@/api/schema/exam-analysis";

type Score = components["schemas"]["ScoreRead"];
type ScoreLabelInput = Pick<Score, "source" | "coefficientYear">;

export const SCORE_PENDING_TEXT = "Türkiye geneli istatistik henüz girilmedi";

// Why a coefficient score is empty (ScoreRead.notComputedReason); ÖSYM's rules, in the student's words.
export const NOT_COMPUTED_TEXT: Record<string, string> = {
  MINIMUM_NET_NOT_MET:
    "Puan hesaplanmadı: bu puan türünün testlerinin birinde en az 0,5 net gerekir",
  SUBJECT_MISSING: "Puan hesaplanmadı: bu puan türünün bir testinin sonucu yok",
  LINKED_RESULT_MISSING: "Puan hesaplanmadı: açıklanmış bir TYT denemesi yok",
  LINKED_MINIMUM_NOT_MET: "Puan hesaplanmadı: TYT puanı hesaplanamadı",
};

// Label of the statistics an exam is scored with (exam list, standard-score model).
export function scoreSourceLabel(source: Score["source"]): string {
  if (source === "INSTITUTIONAL") return "tahmini (kurum içi)";
  if (source === "NATIONAL") return "tahmini (Türkiye geneli)";
  return "tahmini";
}

// An estimated score is never shown without its label (see CLAUDE.md, puanlama).
export function scoreLabel(score: ScoreLabelInput): string {
  if (score.coefficientYear) return `tahmini (${score.coefficientYear} katsayılarıyla)`;
  return scoreSourceLabel(score.source);
}

export type ScoreView = { kind: "pending"; text: string } | { kind: "value"; label: string };

export function describeScore(
  score: Pick<Score, "estimatedScore" | "source" | "coefficientYear" | "notComputedReason">,
): ScoreView {
  if (score.estimatedScore === null) {
    const reason = score.notComputedReason ? NOT_COMPUTED_TEXT[score.notComputedReason] : undefined;
    return { kind: "pending", text: reason ?? SCORE_PENDING_TEXT };
  }
  return { kind: "value", label: scoreLabel(score) };
}

export function scoreExplanation(score: ScoreLabelInput & Pick<Score, "linkedExamName">): string {
  if (score.coefficientYear) {
    const tyt = score.linkedExamName ? ` TYT netleri: ${score.linkedExamName}.` : "";
    return `${score.coefficientYear} tahmini katsayılarıyla hesaplandı.${tyt}`;
  }
  return score.source === "NATIONAL"
    ? "Türkiye geneli ortalama ve standart sapmaya göre hesaplandı"
    : "Kurum içi ortalama ve standart sapmaya göre hesaplandı";
}
