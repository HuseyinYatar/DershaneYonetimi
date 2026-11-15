import { CalendarClock, ChevronLeft, ChevronRight, Plus } from "lucide-react";
import { useState } from "react";
import { useDeactivateSchedule, useSchedules } from "@/api/queries/attendance";
import type { components } from "@/api/schema/attendance";
import { useBranchClassrooms, useBranchTeachers, useUserSubjectNames } from "@/api/queries/user";
import { StatusBadge } from "@/components/StatusBadge";
import { Card, CardHeader } from "@/components/ui/Card";
import { EmptyState, QueryError } from "@/components/ui/EmptyState";
import { Breadcrumb, PageHeader } from "@/components/ui/PageHeader";
import { PillButton, SegButton } from "@/components/ui/Pill";
import { SkeletonRows } from "@/components/ui/Skeleton";
import { TableHead, TableRow, Td, Th } from "@/components/ui/Table";
import { formatClock, formatDayMonth, parseDateOnly } from "@/lib/format";
import { DAY_LABELS, type ScheduleFormValues } from "@/lib/scheduleForm";
import { activeBadge } from "@/lib/status/admin";
import { fullName } from "@/lib/userForm";
import { ScheduleForm } from "./ScheduleForm";

type Schedule = components["schemas"]["ScheduleResponse"];

const BACK = { label: "Yoklama", to: "/sessions" };
const COLS =
  "grid-cols-[minmax(0,1fr)_auto] md:grid-cols-[minmax(0,1fr)_120px_130px_minmax(0,1fr)_90px_150px]";
const PAGE_SIZE = 20;

function toFormValues(s: Schedule): ScheduleFormValues {
  return {
    classroomId: s.classroomId ?? "",
    teacherId: s.teacherId ?? "",
    subjectId: s.subjectId ?? "",
    dayOfWeek: s.dayOfWeek ?? "MONDAY",
    startsAt: formatClock(s.startsAt),
    endsAt: formatClock(s.endsAt),
    validFrom: s.validFrom ?? "",
    validTo: s.validTo ?? "",
  };
}

export function SchedulesPage() {
  const [activeOnly, setActiveOnly] = useState(true);
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<string>();
  const schedules = useSchedules(activeOnly ? true : undefined, page, PAGE_SIZE);
  const classrooms = useBranchClassrooms();
  const teachers = useBranchTeachers();
  const subjects = useUserSubjectNames();
  const deactivate = useDeactivateSchedule();
  const classroomNames = new Map((classrooms.data ?? []).map((c) => [c.id, c.name]));
  const teacherNames = new Map((teachers.data ?? []).map((t) => [t.id, fullName(t)]));
  const list = schedules.data?.content ?? [];
  const totalPages = schedules.data?.page?.totalPages ?? 1;

  return (
    <>
      <PageHeader
        breadcrumb={<Breadcrumb parent={BACK} current="Haftalık program" />}
        title="Haftalık program"
      />
      <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
        <PillButton
          onClick={() => {
            setCreating(!creating);
            setEditing(undefined);
          }}
        >
          <Plus size={14} strokeWidth={1.75} aria-hidden="true" />
          Yeni program
        </PillButton>
        <SegButton
          active={!activeOnly}
          onClick={() => {
            setActiveOnly(!activeOnly);
            setPage(0);
          }}
        >
          Pasifleri de göster
        </SegButton>
      </div>
      {creating && (
        <div className="mt-4">
          <ScheduleForm onDone={() => setCreating(false)} />
        </div>
      )}
      <Card className="mt-4 overflow-hidden">
        <CardHeader
          className="px-5 pt-5 pb-4"
          title={
            <>
              Programlar{" "}
              <span className="font-normal text-dim">
                {schedules.data?.page?.totalElements ?? ""}
              </span>
            </>
          }
        />
        <TableHead cols={COLS}>
          <Th>Sınıf</Th>
          <Th className="hidden md:flex">Gün</Th>
          <Th className="hidden md:flex">Saat</Th>
          <Th className="hidden md:flex">Öğretmen · ders</Th>
          <Th className="hidden md:flex">Durum</Th>
          <Th />
        </TableHead>
        {schedules.isError ? (
          <QueryError error={schedules.error} />
        ) : schedules.isPending ? (
          <div className="pt-4">
            <SkeletonRows rows={4} />
          </div>
        ) : list.length === 0 ? (
          <EmptyState
            icon={CalendarClock}
            title="Program yok"
            text="Henüz haftalık ders programı tanımlanmamış."
          />
        ) : (
          list.map((schedule) => {
            const badge = activeBadge(schedule.active);
            const id = schedule.id ?? "";
            const day = DAY_LABELS[schedule.dayOfWeek ?? "MONDAY"];
            const hours = `${formatClock(schedule.startsAt)} – ${formatClock(schedule.endsAt)}`;
            return (
              <div key={id}>
                <TableRow cols={COLS} className="anim-fade">
                  <Td>
                    <div className="text-sm font-medium">
                      {classroomNames.get(schedule.classroomId) ?? "Sınıf"}
                    </div>
                    <div className="text-[13px] text-dim md:hidden">
                      {day} · {hours}
                    </div>
                    <div className="text-[13px] text-dim">
                      {formatDayMonth(parseDateOnly(schedule.validFrom ?? ""))}
                      {schedule.validTo
                        ? ` – ${formatDayMonth(parseDateOnly(schedule.validTo))}`
                        : " itibaren"}
                    </div>
                  </Td>
                  <Td className="hidden text-sm md:block">{day}</Td>
                  <Td className="hidden text-sm md:block">{hours}</Td>
                  <Td className="hidden text-sm md:block">
                    {teacherNames.get(schedule.teacherId) ?? "Öğretmen"}
                    <span className="text-dim">
                      {" "}
                      · {subjects.data?.get(schedule.subjectId ?? "") ?? "Ders"}
                    </span>
                  </Td>
                  <Td className="hidden md:block">
                    <StatusBadge tone={badge.tone} label={badge.label} size="sm" />
                  </Td>
                  <Td className="flex justify-end gap-2">
                    {schedule.active !== false && (
                      <>
                        <PillButton
                          onClick={() => {
                            setEditing(editing === id ? undefined : id);
                            setCreating(false);
                          }}
                        >
                          Düzenle
                        </PillButton>
                        <PillButton
                          disabled={deactivate.isPending}
                          onClick={() => {
                            if (
                              window.confirm("Program pasife alınsın mı? Oluşmuş dersler kalır.")
                            ) {
                              deactivate.mutate(id);
                            }
                          }}
                        >
                          Kaldır
                        </PillButton>
                      </>
                    )}
                  </Td>
                </TableRow>
                {editing === id && (
                  <div className="border-t border-line bg-surface p-4">
                    <ScheduleForm
                      scheduleId={id}
                      initial={toFormValues(schedule)}
                      onDone={() => setEditing(undefined)}
                    />
                  </div>
                )}
              </div>
            );
          })
        )}
        {totalPages > 1 && (
          <div className="flex items-center justify-between border-t border-line px-5 py-3 text-[13px] text-dim">
            <span>
              Sayfa {page + 1} / {totalPages}
            </span>
            <span className="flex gap-1">
              <button
                type="button"
                aria-label="Önceki sayfa"
                disabled={page === 0}
                onClick={() => setPage(page - 1)}
                className="grid size-8 place-items-center rounded-full hover:bg-sub disabled:opacity-40"
              >
                <ChevronLeft size={16} strokeWidth={1.5} aria-hidden="true" />
              </button>
              <button
                type="button"
                aria-label="Sonraki sayfa"
                disabled={page + 1 >= totalPages}
                onClick={() => setPage(page + 1)}
                className="grid size-8 place-items-center rounded-full hover:bg-sub disabled:opacity-40"
              >
                <ChevronRight size={16} strokeWidth={1.5} aria-hidden="true" />
              </button>
            </span>
          </div>
        )}
      </Card>
    </>
  );
}
