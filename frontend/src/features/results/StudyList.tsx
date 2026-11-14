import { NotebookPen } from "lucide-react";
import type { components } from "@/api/schema/exam-analysis";
import { Card, CardTitle } from "@/components/ui/Card";
import { Skeleton } from "@/components/ui/Skeleton";
import { formatPercent, ratioToPercent } from "@/lib/format";

type WeakTopic = components["schemas"]["WeakTopicRead"];

// "Şu konuları çalış": weak topics of this exam's subjects, weakest first. No "studied" state:
// the API has nowhere to store it.
export function StudyList({
  topics,
  windowSize,
  subjectNames,
  loading = false,
}: {
  topics: WeakTopic[];
  windowSize: number | undefined;
  subjectNames: Map<string, string>;
  loading?: boolean;
}) {
  return (
    <Card id="oneriler" className="p-5">
      <CardTitle icon={<NotebookPen size={18} strokeWidth={1.5} aria-hidden="true" />}>
        Şu konuları çalış
      </CardTitle>
      <p className="mt-1.5 text-[13px] text-dim">
        {windowSize
          ? `Son ${windowSize} denemeye göre, en zayıf konu önce.`
          : "En zayıf konu önce."}
      </p>
      {loading ? (
        <ul className="mt-4" role="status" aria-label="Yükleniyor">
          {[0, 1, 2].map((i) => (
            <li
              key={i}
              className="border-t border-dashed border-[#dadde3] py-3.5 first:border-t-0"
            >
              <div className="flex items-baseline justify-between gap-3">
                <Skeleton className="h-4 w-40 rounded-full" />
                <Skeleton className="h-3.5 w-9 rounded-full" />
              </div>
              <Skeleton className="mt-2 h-3.5 w-52 max-w-full rounded-full" />
            </li>
          ))}
        </ul>
      ) : topics.length === 0 ? (
        <p className="mt-4 text-[13px] text-dim">Bu denemede zayıf konun görünmüyor.</p>
      ) : (
        <ul className="mt-4">
          {topics.map((topic) => (
            <li
              key={topic.topicId}
              className="border-t border-dashed border-[#dadde3] py-3.5 first:border-t-0"
            >
              <div className="flex items-baseline justify-between gap-3">
                <div className="text-sm leading-snug font-medium">{topic.topicName}</div>
                <span className="shrink-0 text-[13px] text-dim">
                  {formatPercent(ratioToPercent(topic.successRate))}
                </span>
              </div>
              <p className="mt-1 text-[13px] leading-snug text-dim">
                {subjectNames.get(topic.subjectId) ?? "Ders"} · {topic.totalQuestions} sorudan{" "}
                {topic.totalCorrect} doğru
              </p>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}
