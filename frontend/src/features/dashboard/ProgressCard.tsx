import { useProgress, useStudentResults } from "@/api/queries/exam";
import { DeltaBadge } from "@/components/DeltaBadge";
import { NetProgress } from "@/components/NetProgress";
import { Card, CardTitle } from "@/components/ui/Card";
import { EmptyState, QueryError } from "@/components/ui/EmptyState";
import { LineSkeleton } from "@/components/ui/ChartSkeletons";
import { formatNet } from "@/lib/format";
import { netDelta } from "@/lib/progress";

export function ProgressCard({ studentId }: { studentId: string }) {
  const results = useStudentResults(studentId);
  const examTypeId = results.data?.[0]?.examTypeId;
  const progress = useProgress(studentId, examTypeId);
  const points = progress.data?.points ?? [];
  const last = points[points.length - 1];
  const delta = netDelta(points);

  return (
    <Card className="p-5 lg:col-span-2 wide:col-span-3">
      <div className="flex flex-col wide:flex-row wide:gap-6">
        <div className="flex shrink-0 flex-col wide:w-[250px]">
          <CardTitle>Net gelişimi</CardTitle>
          {last && (
            <div className="mt-4 wide:mt-auto">
              <div className="text-[56px] leading-none font-light tracking-[-0.045em]">
                {formatNet(last.net)}
                <span className="ml-2 text-xl font-normal tracking-[-0.01em] text-[#6b7280]">
                  net
                </span>
              </div>
              {delta !== null && (
                <div className="mt-3 flex items-center gap-2 text-[13px] whitespace-nowrap text-dim">
                  <DeltaBadge delta={delta} />
                  önceki denemeye göre
                </div>
              )}
            </div>
          )}
        </div>
        <div className="mt-4 min-w-0 flex-1 wide:mt-0">
          {results.isError ? (
            <QueryError error={results.error} />
          ) : progress.isError ? (
            <QueryError error={progress.error} />
          ) : results.isPending || (examTypeId && progress.isPending) ? (
            <LineSkeleton />
          ) : points.length === 0 ? (
            <EmptyState text="Henüz açıklanmış deneme yok." />
          ) : (
            <NetProgress points={points} />
          )}
        </div>
      </div>
    </Card>
  );
}
