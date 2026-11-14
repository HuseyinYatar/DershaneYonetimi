import { ClipboardList, Plus } from "lucide-react";
import { Link } from "react-router";
import { useTasks } from "@/api/queries/task";
import { useMyClassrooms, useMyStudentNames } from "@/api/queries/user";
import { useActor } from "@/auth/useActor";
import { StatusBadge } from "@/components/StatusBadge";
import { Card, CardHeader } from "@/components/ui/Card";
import { EmptyState, QueryError } from "@/components/ui/EmptyState";
import { PageHeader } from "@/components/ui/PageHeader";
import { PillLink } from "@/components/ui/Pill";
import { SkeletonRows } from "@/components/ui/Skeleton";
import { TableHead, TableRow, Td, Th } from "@/components/ui/Table";
import { formatDateLong } from "@/lib/format";
import { taskActiveBadge } from "@/lib/status/tasks";
import { RECURRENCE_LABELS, describeSchedule } from "@/lib/taskForm";

const COLS =
  "grid-cols-[minmax(0,1fr)_auto] md:grid-cols-[minmax(0,1.3fr)_110px_minmax(0,1.3fr)_100px]";

export function GivenTasksPage() {
  const actor = useActor();
  const tasks = useTasks(actor?.id);
  const classrooms = useMyClassrooms();
  const studentNames = useMyStudentNames();
  const classroomNames = new Map((classrooms.data ?? []).map((c) => [c.id, c.name]));
  const list = tasks.data ?? [];

  return (
    <>
      <PageHeader eyebrow={formatDateLong(new Date())} title="Görevler" />
      <div className="mt-4">
        <PillLink to="/given-tasks/new">
          <Plus size={14} strokeWidth={1.5} aria-hidden="true" />
          Yeni görev
        </PillLink>
      </div>
      <Card className="mt-4 overflow-hidden">
        <CardHeader
          className="px-5 pt-5 pb-4"
          title={
            <>
              Verdiğim görevler <span className="font-normal text-dim">{list.length}</span>
            </>
          }
        />
        <TableHead cols={COLS}>
          <Th>Görev</Th>
          <Th divider className="hidden md:flex">
            Tekrar
          </Th>
          <Th divider className="hidden md:flex">
            Takvim
          </Th>
          <Th divider>Durum</Th>
        </TableHead>
        {tasks.isError ? (
          <QueryError error={tasks.error} />
        ) : tasks.isPending ? (
          <div className="pt-4">
            <SkeletonRows rows={4} />
          </div>
        ) : list.length === 0 ? (
          <EmptyState
            icon={ClipboardList}
            title="Henüz görev vermedin"
            text="Sınıfına veya bir öğrenciye görev vererek başla."
            action={<PillLink to="/given-tasks/new">Yeni görev</PillLink>}
          />
        ) : (
          list.map((task) => {
            const badge = taskActiveBadge(task.active);
            const target =
              task.targetType === "CLASSROOM"
                ? `Sınıf · ${classroomNames.get(task.targetId) ?? "—"}`
                : `Öğrenci · ${studentNames.get(task.targetId ?? "") ?? "—"}`;
            const schedule = describeSchedule(task);
            return (
              <TableRow
                key={task.id}
                cols={COLS}
                className="relative items-start gap-y-1 hover:bg-surface"
              >
                <Td>
                  <Link
                    to={`/given-tasks/${task.id}`}
                    className="block text-sm leading-5 font-medium after:absolute after:inset-0 hover:underline"
                  >
                    {task.title}
                  </Link>
                  <div className="text-[13px] text-dim">{target}</div>
                  <div className="text-[13px] text-dim md:hidden">
                    {RECURRENCE_LABELS[task.recurrence!]} · {schedule}
                  </div>
                </Td>
                <Td divider className="hidden text-sm md:block">
                  {RECURRENCE_LABELS[task.recurrence!]}
                </Td>
                <Td divider className="hidden text-sm md:block">
                  {schedule}
                </Td>
                <Td divider>
                  <StatusBadge tone={badge.tone} label={badge.label} />
                </Td>
              </TableRow>
            );
          })
        )}
      </Card>
    </>
  );
}
