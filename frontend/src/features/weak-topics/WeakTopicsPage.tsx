import { useParams } from "react-router";
import { useExamSubjectNames, useWeakTopics } from "@/api/queries/exam";
import { Card } from "@/components/ui/Card";
import { EmptyState, QueryError } from "@/components/ui/EmptyState";
import { PageHeader } from "@/components/ui/PageHeader";
import { SkeletonRows } from "@/components/ui/Skeleton";
import { formatPercent, ratioToPercent } from "@/lib/format";

// Weakest first; the API returns them in that order. It sends no advice text, so each row shows
// what it does know: success rate and correct/total questions over the measured exams.
export function WeakTopicsPage() {
  const { studentId = "" } = useParams();
  const weak = useWeakTopics(studentId);
  const subjectNames = useExamSubjectNames();
  const topics = weak.data?.weakTopics ?? [];

  return (
    <>
      <PageHeader
        eyebrow={
          weak.data
            ? `Son ${weak.data.windowSize} denemeye göre, en zayıf konu önce`
            : "Önerilen çalışma"
        }
        title="Zayıf konular"
      />
      <Card className="mt-6">
        {weak.isError ? (
          <QueryError error={weak.error} />
        ) : weak.isPending ? (
          <div className="pt-5">
            <SkeletonRows rows={4} />
          </div>
        ) : topics.length === 0 ? (
          <EmptyState text="Şu an zayıf konun yok." />
        ) : (
          <ol>
            {topics.map((topic) => {
              const percent = ratioToPercent(topic.successRate);
              return (
                <li key={topic.topicId} className="border-t border-line px-5 py-4 first:border-t-0">
                  <div className="flex items-baseline justify-between gap-3">
                    <div className="min-w-0">
                      <div className="text-[15px] leading-snug font-medium">{topic.topicName}</div>
                      <div className="text-[13px] text-dim">
                        {subjectNames.get(topic.subjectId) ?? "Ders"} · {topic.measuredExamCount}{" "}
                        denemede ölçüldü · {topic.totalQuestions} sorudan {topic.totalCorrect} doğru
                      </div>
                    </div>
                    <div className="shrink-0 text-[28px] leading-none font-light tracking-[-0.04em]">
                      {formatPercent(percent)}
                    </div>
                  </div>
                  <div className="mt-3 h-1.5 overflow-hidden rounded-full bg-[#eceef1]">
                    <div
                      className="anim-grow-x h-full rounded-full bg-brand"
                      style={{ width: `${percent}%` }}
                    />
                  </div>
                </li>
              );
            })}
          </ol>
        )}
      </Card>
    </>
  );
}
