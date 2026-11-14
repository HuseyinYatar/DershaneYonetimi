import { useState } from "react";
import { useAssignment, useTaskAssignments } from "@/api/queries/task";
import { useMyStudentNames } from "@/api/queries/user";
import { StatusBadge } from "@/components/StatusBadge";
import { StatusFilter } from "@/components/StatusFilter";
import { Card, CardHeader } from "@/components/ui/Card";
import { EmptyState, QueryError } from "@/components/ui/EmptyState";
import { PillButton } from "@/components/ui/Pill";
import { Skeleton, SkeletonRows } from "@/components/ui/Skeleton";
import { TableHead, TableRow, Td, Th } from "@/components/ui/Table";
import { studentLabel, type Assignment } from "@/lib/assignments";
import { formatDayMonth, formatDeadline, parseDateOnly } from "@/lib/format";
import { assignmentBadge, type AssignmentStatus } from "@/lib/status/tasks";
import { EvaluationForm } from "./EvaluationForm";

const COLS = "grid-cols-[minmax(0,1fr)_auto] md:grid-cols-[minmax(0,1fr)_100px_150px_150px_100px]";
const STATUSES = ["ASSIGNED", "OVERDUE", "SUBMITTED", "GRADED", "CANCELLED"] as const;

function ReviewPanel({ assignmentId }: { assignmentId: string }) {
  const detail = useAssignment(assignmentId);
  if (detail.isError) return <QueryError error={detail.error} />;
  if (!detail.data) {
    return (
      <div role="status" aria-label="Yükleniyor" className="flex flex-col gap-3">
        <Skeleton className="h-16" />
        <Skeleton className="h-24" />
      </div>
    );
  }
  const { submission, evaluation } = detail.data;
  return (
    <div className="grid gap-5 lg:grid-cols-2">
      <div>
        <div className="text-[13px] text-dim">
          Teslim
          {submission?.submittedAt && ` · ${formatDeadline(new Date(submission.submittedAt))}`}
          {submission?.late && " · geç teslim"}
        </div>
        <p className="mt-2 text-sm leading-relaxed whitespace-pre-wrap">
          {submission?.description || "Açıklama yok."}
        </p>
        {(submission?.fileIds?.length ?? 0) > 0 && (
          <p className="mt-2 text-[13px] text-dim">{submission?.fileIds?.length} ek dosya</p>
        )}
      </div>
      <EvaluationForm
        key={evaluation?.evaluatedAt ?? "new"}
        assignmentId={assignmentId}
        score={evaluation?.score}
        feedback={evaluation?.feedback}
      />
    </div>
  );
}

function AssignmentRow({ row, name }: { row: Assignment; name: string }) {
  const [open, setOpen] = useState(false);
  const badge = assignmentBadge(row.status!);
  const reviewable = row.status === "SUBMITTED" || row.status === "GRADED";
  const due = row.dueAt ? formatDeadline(new Date(row.dueAt)) : "—";
  const occurrence = row.occurrenceDate ? formatDayMonth(parseDateOnly(row.occurrenceDate)) : "—";

  return (
    <div>
      <TableRow cols={COLS}>
        <Td>
          <div className="text-sm font-medium">{name}</div>
          <div className="text-[13px] text-dim md:hidden">{due}</div>
        </Td>
        <Td className="hidden text-sm md:block">{occurrence}</Td>
        <Td className="hidden text-sm md:block">{due}</Td>
        <Td className="hidden md:block">
          <StatusBadge tone={badge.tone} label={badge.label} />
        </Td>
        <Td className="flex items-center justify-end gap-2">
          <span className="md:hidden">
            <StatusBadge tone={badge.tone} label={badge.label} iconOnly />
          </span>
          {reviewable && (
            <PillButton aria-expanded={open} onClick={() => setOpen(!open)}>
              {open ? "Kapat" : row.status === "GRADED" ? "Görüntüle" : "Değerlendir"}
            </PillButton>
          )}
        </Td>
      </TableRow>
      {open && (
        <div className="border-t border-line bg-surface px-5 py-5">
          <ReviewPanel assignmentId={row.id!} />
        </div>
      )}
    </div>
  );
}

export function AssignmentsCard({ taskId }: { taskId: string }) {
  const [status, setStatus] = useState<AssignmentStatus>();
  const assignments = useTaskAssignments(taskId, status);
  const names = useMyStudentNames();
  const list = assignments.data ?? [];

  return (
    <Card className="overflow-hidden">
      <CardHeader
        className="px-5 pt-5 pb-4"
        title={
          <>
            Atamalar <span className="font-normal text-dim">{list.length}</span>
          </>
        }
        action={<StatusFilter statuses={STATUSES} value={status} onChange={setStatus} />}
      />
      <TableHead cols={COLS}>
        <Th>Öğrenci</Th>
        <Th className="hidden md:flex">Gün</Th>
        <Th className="hidden md:flex">Son teslim</Th>
        <Th className="hidden md:flex">Durum</Th>
        <Th className="hidden md:flex" />
      </TableHead>
      {assignments.isError ? (
        <QueryError error={assignments.error} />
      ) : assignments.isPending ? (
        <div className="pt-4">
          <SkeletonRows rows={4} />
        </div>
      ) : list.length === 0 ? (
        <EmptyState text={status ? "Bu süzgece uyan atama yok." : "Henüz atama oluşmadı."} />
      ) : (
        list.map((row) => (
          <AssignmentRow key={row.id} row={row} name={studentLabel(names, row.studentId)} />
        ))
      )}
    </Card>
  );
}
