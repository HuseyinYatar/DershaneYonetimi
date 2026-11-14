import { ArrowRight } from "lucide-react";
import { Link } from "react-router";
import { useExamSubjectNames, useWeakTopics } from "@/api/queries/exam";
import { CardTitle } from "@/components/ui/Card";
import { isUnavailable } from "@/components/ui/EmptyState";
import { WeakTopicsSkeleton } from "@/components/ui/ChartSkeletons";
import { ratioToPercent } from "@/lib/format";

// The one violet-filled card: the three weakest topics over the student's last N exams.
export function WeakTopicsCard({ studentId }: { studentId: string }) {
  const weak = useWeakTopics(studentId);
  const subjectNames = useExamSubjectNames();
  const top = (weak.data?.weakTopics ?? []).slice(0, 3);

  return (
    <section className="flex flex-col rounded-[20px] bg-brand p-5 text-white lg:col-span-2 wide:col-span-1">
      <div className="flex items-center justify-between gap-3">
        <CardTitle>Zayıf konular</CardTitle>
        {weak.data && (
          <span className="inline-flex h-8 items-center rounded-full bg-white/[0.18] px-3 text-[13px]">
            Son {weak.data.windowSize} deneme
          </span>
        )}
      </div>
      <div className="mt-5 flex-1">
        {weak.isPending ? (
          <WeakTopicsSkeleton />
        ) : weak.isError ? (
          <p className="py-8 text-center text-[13px] text-white/85">
            {isUnavailable(weak.error) ? "Bu içerik görüntülenemiyor." : "Veri alınamadı."}
          </p>
        ) : top.length === 0 ? (
          <p className="py-8 text-center text-[13px] text-white/85">Şu an zayıf konun yok.</p>
        ) : (
          <ul>
            {top.map((topic) => {
              const percent = ratioToPercent(topic.successRate);
              return (
                <li
                  key={topic.topicId}
                  className="border-t border-white/[0.22] py-4 first:border-t-0"
                >
                  <div className="flex items-end justify-between gap-3">
                    <div className="min-w-0">
                      <div className="text-[15px] leading-snug font-medium">{topic.topicName}</div>
                      <div className="text-[13px] text-white/85">
                        {subjectNames.get(topic.subjectId) ?? " "}
                      </div>
                    </div>
                    <div className="shrink-0 text-[32px] leading-none font-normal tracking-[-0.045em]">
                      <span className="mr-0.5 text-xl text-white/85">%</span>
                      {percent}
                    </div>
                  </div>
                  <div className="mt-3 h-[3px] rounded-full bg-white/25">
                    <div
                      className="anim-grow-x h-full rounded-full bg-white"
                      style={{ width: `${percent}%` }}
                    />
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </div>
      <Link
        to={`/students/${studentId}/weak-topics`}
        className="mt-2 inline-flex h-11 items-center justify-between rounded-full bg-white px-5 text-sm font-medium text-ink"
      >
        Şu konuları çalış
        <ArrowRight size={16} strokeWidth={1.5} aria-hidden="true" />
      </Link>
    </section>
  );
}
