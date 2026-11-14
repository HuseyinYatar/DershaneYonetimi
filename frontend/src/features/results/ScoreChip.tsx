import type { components } from "@/api/schema/exam-analysis";
import { formatNet } from "@/lib/format";
import { describeScore } from "@/lib/score";

type Score = components["schemas"]["ScoreRead"];

// Compact "391,4 [tahmini (kurum içi)]" for lists; a missing national score explains itself instead.
export function ScoreInline({ score }: { score: Score }) {
  const view = describeScore(score);
  return (
    <span className="flex flex-wrap items-center gap-x-2 gap-y-1">
      <span className="text-[13px] text-dim">{score.scoreTypeCode}</span>
      {view.kind === "pending" ? (
        <span className="text-[13px] text-dim">{view.text}</span>
      ) : (
        <>
          <span className="text-[15px] font-medium">{formatNet(score.estimatedScore)}</span>
          <span className="rounded-full bg-brand-tint px-2 py-0.5 text-[11px] font-medium whitespace-nowrap text-brand-ink">
            {view.label}
          </span>
        </>
      )}
    </span>
  );
}
