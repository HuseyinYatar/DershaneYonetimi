import { Calendar, FileText, LoaderCircle } from "lucide-react";
import { Link } from "react-router";
import { StatusBadge } from "@/components/StatusBadge";
import { Card, CardHeader } from "@/components/ui/Card";
import { EmptyState, QueryError } from "@/components/ui/EmptyState";
import { PillLink } from "@/components/ui/Pill";
import { SkeletonRows } from "@/components/ui/Skeleton";
import { TableHead, TableRow, Td, Th } from "@/components/ui/Table";
import { useAssignments } from "@/api/queries/task";
import { upcomingOrOverdue } from "@/lib/assignments";
import { formatDeadline } from "@/lib/format";
import { assignmentBadge } from "@/lib/status/tasks";

const COLS = "grid-cols-[minmax(0,1fr)_auto] sm:grid-cols-[minmax(0,1fr)_130px_170px]";

export function TasksCard({ studentId }: { studentId: string }) {
  const assignments = useAssignments(studentId);
  const rows = upcomingOrOverdue(assignments.data ?? []);

  return (
    <Card className="overflow-hidden lg:col-span-2 wide:col-start-1 wide:row-start-2">
      <CardHeader
        title="Görevlerim"
        className="px-5 pt-5 pb-4"
        action={<PillLink to="/tasks">Tümünü gör</PillLink>}
      />
      <TableHead cols={COLS}>
        <Th icon={FileText}>Görev</Th>
        <Th icon={Calendar} divider className="hidden sm:flex">
          Son teslim
        </Th>
        <Th icon={LoaderCircle} divider>
          Durum
        </Th>
      </TableHead>
      {assignments.isError ? (
        <QueryError error={assignments.error} />
      ) : assignments.isPending ? (
        <div className="pt-4">
          <SkeletonRows rows={3} />
        </div>
      ) : rows.length === 0 ? (
        <EmptyState text="Bekleyen görevin yok." />
      ) : (
        rows.map((row) => {
          const badge = assignmentBadge(row.status!);
          const dueText = row.dueAt ? formatDeadline(new Date(row.dueAt)) : "—";
          return (
            <TableRow key={row.id} cols={COLS} className="relative gap-y-1 hover:bg-surface">
              <Td>
                <Link
                  to={`/tasks/${row.id}`}
                  className="block text-sm leading-snug font-medium after:absolute after:inset-0 hover:underline"
                >
                  {row.taskTitle}
                </Link>
                <div className="text-[13px] text-dim sm:hidden">{dueText}</div>
              </Td>
              <Td divider className="hidden text-sm sm:block">
                {dueText}
              </Td>
              <Td divider>
                <span className="sm:hidden">
                  <StatusBadge tone={badge.tone} label={badge.label} iconOnly />
                </span>
                <span className="hidden sm:inline-flex">
                  <StatusBadge tone={badge.tone} label={badge.label} />
                </span>
              </Td>
            </TableRow>
          );
        })
      )}
    </Card>
  );
}
