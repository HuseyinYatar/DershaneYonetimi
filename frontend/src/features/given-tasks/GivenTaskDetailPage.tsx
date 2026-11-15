import { useState } from "react";
import { useParams } from "react-router";
import { useDeactivateTask, useTask } from "@/api/queries/task";
import { useMyClassrooms, useMyStudentNames } from "@/api/queries/user";
import { StatusBadge } from "@/components/StatusBadge";
import { Card, CardTitle } from "@/components/ui/Card";
import { QueryError } from "@/components/ui/EmptyState";
import { Breadcrumb, PageHeader } from "@/components/ui/PageHeader";
import { PillButton } from "@/components/ui/Pill";
import { Skeleton } from "@/components/ui/Skeleton";
import { taskActiveBadge } from "@/lib/status/tasks";
import { RECURRENCE_LABELS, describeSchedule } from "@/lib/taskForm";
import { AssignmentsCard } from "./AssignmentsCard";
import { TaskEditForm } from "./TaskEditForm";

const BACK = { label: "Görevler", to: "/given-tasks" };

export function GivenTaskDetailPage() {
  const { taskId = "" } = useParams();
  const task = useTask(taskId);
  const deactivate = useDeactivateTask(taskId);
  const classrooms = useMyClassrooms();
  const studentNames = useMyStudentNames();
  const [editing, setEditing] = useState(false);
  const [confirming, setConfirming] = useState(false);

  if (task.isError) {
    return (
      <>
        <PageHeader breadcrumb={<Breadcrumb parent={BACK} current="Görev" />} title="Görev" />
        <QueryError error={task.error} className="py-16" />
      </>
    );
  }
  const data = task.data;
  if (!data) {
    return (
      <>
        <PageHeader breadcrumb={<Breadcrumb parent={BACK} current="…" />} title="Görev" />
        <div role="status" aria-label="Yükleniyor" className="mt-6 flex flex-col gap-4">
          <Skeleton className="h-40" />
          <Skeleton className="h-64" />
        </div>
      </>
    );
  }

  const badge = taskActiveBadge(data.active);
  const target =
    data.targetType === "CLASSROOM"
      ? `Sınıf · ${classrooms.data?.find((c) => c.id === data.targetId)?.name ?? "—"}`
      : `Öğrenci · ${studentNames.get(data.targetId ?? "") ?? "—"}`;

  return (
    <>
      <PageHeader
        breadcrumb={<Breadcrumb parent={BACK} current={data.title ?? "Görev"} />}
        eyebrow={`${RECURRENCE_LABELS[data.recurrence!]} · ${describeSchedule(data)}`}
        title={data.title ?? "Görev"}
      />
      <div className="mt-5 flex flex-col gap-4">
        <Card className="p-5">
          <div className="flex items-center justify-between gap-3">
            <CardTitle>Görev</CardTitle>
            <StatusBadge tone={badge.tone} label={badge.label} />
          </div>
          <div className="mt-1.5 text-[13px] text-dim">{target}</div>
          {editing ? (
            <TaskEditForm key={data.title} task={data} onDone={() => setEditing(false)} />
          ) : (
            <>
              <p className="mt-4 text-sm leading-relaxed whitespace-pre-wrap">
                {data.description || "Açıklama yok."}
              </p>
              <div className="mt-5 flex flex-wrap items-center gap-2">
                <PillButton onClick={() => setEditing(true)}>Düzenle</PillButton>
                {data.active && !confirming && (
                  <PillButton onClick={() => setConfirming(true)}>Pasife al</PillButton>
                )}
                {data.active && confirming && (
                  <span className="flex flex-wrap items-center gap-2 text-[13px]">
                    Yeni atama verilmez, var olanlar kalır.
                    <PillButton
                      disabled={deactivate.isPending}
                      onClick={() =>
                        deactivate.mutate(undefined, { onSettled: () => setConfirming(false) })
                      }
                    >
                      Evet, pasife al
                    </PillButton>
                    <PillButton onClick={() => setConfirming(false)}>Vazgeç</PillButton>
                  </span>
                )}
              </div>
            </>
          )}
        </Card>
        <AssignmentsCard taskId={taskId} />
      </div>
    </>
  );
}
