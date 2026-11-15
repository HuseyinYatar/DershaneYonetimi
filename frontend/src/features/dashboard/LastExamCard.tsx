import { ChevronRight } from "lucide-react";
import { BarChart } from "@/components/charts/BarChart";
import { subjectBars } from "@/lib/subjectBars";
import { Card, CardHeader } from "@/components/ui/Card";
import { EmptyState, QueryError } from "@/components/ui/EmptyState";
import { PillLink } from "@/components/ui/Pill";
import { BarsSkeleton } from "@/components/ui/ChartSkeletons";
import { useProgress, useStudentResult, useStudentResults } from "@/api/queries/exam";
import { formatDayMonthLong, formatNet, parseDateOnly } from "@/lib/format";

export function LastExamCard({ studentId }: { studentId: string }) {
  const results = useStudentResults(studentId);
  const latest = results.data?.[0];
  const detail = useStudentResult(studentId, latest?.mockExamId);
  const progress = useProgress(studentId, latest?.examTypeId);

  const body = () => {
    if (results.isError) return <QueryError error={results.error} />;
    if (results.isPending)
      return (
        <div className="mt-4 rounded-2xl bg-sub p-4 pb-3">
          <div className="h-12 w-28 animate-pulse rounded-xl bg-[#E4E6EB]" />
          <div className="mt-5">
            <BarsSkeleton />
          </div>
        </div>
      );
    if (!latest) return <EmptyState text="Henüz açıklanmış deneme yok." />;

    const chart = detail.data ? subjectBars(detail.data, progress.data?.points ?? []) : null;
    return (
      <div className="mt-4 rounded-2xl bg-sub p-4 pb-3">
        <div className="flex items-end justify-between gap-3">
          <div className="text-[56px] leading-none font-light tracking-[-0.045em]">
            {formatNet(latest.net)}
            <span className="ml-2 text-xl font-normal tracking-[-0.01em] text-[#6b7280]">net</span>
          </div>
          <div className="pb-1 text-right text-[13px] leading-snug text-dim">
            {latest.examName}
            <br />
            {formatDayMonthLong(parseDateOnly(latest.examDate))}
          </div>
        </div>
        <div className="mt-5">
          {chart ? (
            <BarChart
              bars={chart.bars}
              max={chart.max}
              ariaLabel={`Derslere göre net: ${chart.bars.map((b) => `${b.label} ${formatNet(b.value)}`).join(", ")}`}
            />
          ) : detail.isError ? (
            <QueryError error={detail.error} className="py-10" />
          ) : (
            <BarsSkeleton />
          )}
        </div>
      </div>
    );
  };

  return (
    <Card className="p-5">
      <CardHeader
        title="Son deneme"
        action={
          latest && (
            <PillLink to={`/students/${studentId}/results/${latest.mockExamId}`}>
              Ayrıntılar
              <ChevronRight size={16} strokeWidth={1.5} aria-hidden="true" />
            </PillLink>
          )
        }
      />
      {body()}
    </Card>
  );
}
