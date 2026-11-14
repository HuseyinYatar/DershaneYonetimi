import { Globe } from "lucide-react";
import type { components } from "@/api/schema/exam-analysis";
import { CardTitle } from "@/components/ui/Card";
import { formatInt, formatNet } from "@/lib/format";
import { cn } from "@/lib/utils";
import { describeScore, scoreExplanation } from "@/lib/score";

type Score = components["schemas"]["ScoreRead"];

// Violet card with the title, the big number, the explanation and the rank row greyed out.
export function ScoreCardSkeleton() {
  return (
    <section
      aria-hidden="true"
      className="flex animate-pulse flex-col rounded-[20px] bg-brand p-5 lg:col-span-2 wide:col-span-1"
    >
      <div className="h-5 w-28 rounded-full bg-white/25" />
      <div className="mt-8 h-[76px] w-44 rounded-2xl bg-white/25" />
      <div className="mt-4 h-3.5 w-3/4 rounded-full bg-white/20" />
      <div className="mt-auto flex items-center justify-between border-t border-white/[0.22] pt-3.5">
        <div className="h-4 w-24 rounded-full bg-white/20" />
        <div className="h-4 w-8 rounded-full bg-white/25" />
      </div>
    </section>
  );
}

// The violet card: one block per score type. Every number carries its "tahmini (...)" chip.
export function ScoreCard({ scores }: { scores: Score[] }) {
  return (
    <section className="flex flex-col rounded-[20px] bg-brand p-5 text-white lg:col-span-2 wide:col-span-1">
      {scores.map((score, i) => {
        const view = describeScore(score);
        return (
          <div
            key={score.scoreTypeId}
            className={cn(
              "flex flex-col",
              i > 0 && "mt-8 border-t border-white/[0.22] pt-6",
              i === scores.length - 1 && "flex-1",
            )}
          >
            <div className="flex items-center justify-between gap-3">
              <CardTitle>{score.scoreTypeCode} puanı</CardTitle>
              {view.kind === "value" && (
                <span className="inline-flex h-7 items-center rounded-full bg-white px-3 text-xs font-medium text-brand-ink">
                  {view.label}
                </span>
              )}
            </div>
            {view.kind === "value" ? (
              <>
                <div className="mt-8 text-[76px] leading-none font-light tracking-[-0.045em]">
                  {formatNet(score.estimatedScore)}
                </div>
                <p className="mt-4 text-[13px] text-white/85">{scoreExplanation(score.source)}</p>
              </>
            ) : (
              <div className="mt-6 flex items-start gap-3 text-sm text-white/90">
                <Globe size={16} strokeWidth={1.5} aria-hidden="true" className="mt-0.5 shrink-0" />
                <span>{view.text}</span>
              </div>
            )}
            {score.branchRank !== null && (
              <div className="mt-auto flex items-center justify-between border-t border-white/[0.22] py-3.5">
                <span className="text-sm text-white/90">Kurum sırası</span>
                <span className="text-[15px] font-medium">{formatInt(score.branchRank)}</span>
              </div>
            )}
          </div>
        );
      })}
    </section>
  );
}
