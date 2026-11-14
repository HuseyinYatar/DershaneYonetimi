import { Link } from "react-router";
import { useAssignment } from "@/api/queries/task";
import { Card, CardHeader } from "@/components/ui/Card";
import { EmptyState, QueryError } from "@/components/ui/EmptyState";
import { PillLink } from "@/components/ui/Pill";
import { Skeleton, SkeletonRows } from "@/components/ui/Skeleton";
import { studentLabel, type Assignment } from "@/lib/assignments";
import { formatDeadline } from "@/lib/format";

// The list rows carry no submission time, so each of the few rows loads its detail.
function PendingRow({ row, name }: { row: Assignment; name: string }) {
  const submittedAt = useAssignment(row.id).data?.submission?.submittedAt;
  return (
    <div className="border-t border-line px-5 py-4">
      <Link to={`/given-tasks/${row.taskId}`} className="text-sm font-medium hover:underline">
        {row.taskTitle}
      </Link>
      <div className="mt-0.5 flex flex-wrap items-center gap-x-2 text-[13px] text-dim">
        {name}
        <span aria-hidden="true">·</span>
        {submittedAt ? (
          `Teslim ${formatDeadline(new Date(submittedAt))}`
        ) : (
          <Skeleton className="h-3.5 w-24 rounded-full" />
        )}
      </div>
    </div>
  );
}

export function PendingGradingCard({
  rows,
  isPending,
  error,
  studentNames,
}: {
  rows: Assignment[];
  isPending: boolean;
  error: unknown;
  studentNames: Map<string, string>;
}) {
  return (
    <Card className="overflow-hidden">
      <CardHeader
        title="Değerlendirme bekleyenler"
        className="px-5 pt-5 pb-4"
        action={<PillLink to="/given-tasks">Görevler</PillLink>}
      />
      {error ? (
        <QueryError error={error} />
      ) : isPending ? (
        <SkeletonRows rows={3} />
      ) : rows.length === 0 ? (
        <EmptyState text="Değerlendirme bekleyen teslim yok." />
      ) : (
        rows.map((row) => (
          <PendingRow key={row.id} row={row} name={studentLabel(studentNames, row.studentId)} />
        ))
      )}
    </Card>
  );
}
