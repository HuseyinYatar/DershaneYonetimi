import { CalendarX, ChevronLeft, ChevronRight } from "lucide-react";
import { useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { useSearchParams } from "react-router";
import {
  branchSessionsQuery,
  sessionsQuery,
  useBranchSessions,
  useSessions,
} from "@/api/queries/attendance";
import { useBranchClassrooms, useBranchTeachers, useMyClassrooms } from "@/api/queries/user";
import { useActor } from "@/auth/useActor";
import { StatusBadge } from "@/components/StatusBadge";
import { WeekStrip } from "@/components/WeekStrip";
import { Card, CardHeader } from "@/components/ui/Card";
import { EmptyState, QueryError } from "@/components/ui/EmptyState";
import { PageHeader } from "@/components/ui/PageHeader";
import { PillButton, PillLink } from "@/components/ui/Pill";

import { SkeletonRows } from "@/components/ui/Skeleton";
import { TableHead, TableRow, Td, Th } from "@/components/ui/Table";
import {
  formatClock,
  formatDateLong,
  formatFullDateWithWeekday,
  parseDateOnly,
} from "@/lib/format";
import { sessionBadge } from "@/lib/status/sessions";
import { fullName } from "@/lib/userForm";
import { cn } from "@/lib/utils";
import { dateKey, weekDays, weekRangeLabel } from "@/lib/week";
import { NewSessionForm } from "./NewSessionForm";

const COLS = "grid-cols-[minmax(0,1fr)_auto] md:grid-cols-[130px_minmax(0,1fr)_170px_120px]";
const ADMIN_COLS =
  "grid-cols-[minmax(0,1fr)_auto] md:grid-cols-[110px_minmax(0,1fr)_minmax(0,1fr)_170px_120px]";
const DAY = /^\d{4}-\d{2}-\d{2}$/;

export function SessionsPage() {
  const actor = useActor();
  // Admins see every session of the branch; teachers only their own (the service enforces it too).
  const isAdmin = !!actor?.roles.includes("admin");
  const [creating, setCreating] = useState(false);
  // Set by the arrows only, so the first render and day clicks do not animate.
  const [direction, setDirection] = useState<"left" | "right" | null>(null);
  const [params, setParams] = useSearchParams();
  const dayParam = params.get("day");
  const day = dayParam && DAY.test(dayParam) ? dayParam : dateKey(new Date());
  const days = weekDays(parseDateOnly(day));
  const range = { from: dateKey(days[0]), to: dateKey(days[6]) };
  const teacherSessions = useSessions(isAdmin ? undefined : actor?.id, range);
  const branchSessions = useBranchSessions(range, isAdmin);
  const sessions = isAdmin ? branchSessions : teacherSessions;
  const myClassrooms = useMyClassrooms(!isAdmin);
  const branchClassrooms = useBranchClassrooms(isAdmin);
  const teachers = useBranchTeachers(isAdmin);
  const classroomNames = new Map(
    ((isAdmin ? branchClassrooms.data : myClassrooms.data) ?? []).map((c) => [c.id, c.name]),
  );
  const teacherNames = new Map((teachers.data ?? []).map((t) => [t.id, fullName(t)]));
  const cols = isAdmin ? ADMIN_COLS : COLS;

  // Warm the neighbouring weeks so an arrow click shows cached data instead of a loading flash.
  const queryClient = useQueryClient();
  const teacherId = actor?.id;
  useEffect(() => {
    for (const shift of [-7, 7]) {
      const start = parseDateOnly(range.from);
      start.setDate(start.getDate() + shift);
      const near = weekDays(start);
      const nearRange = { from: dateKey(near[0]), to: dateKey(near[6]) };
      if (isAdmin) void queryClient.prefetchQuery(branchSessionsQuery(nearRange));
      else if (teacherId) void queryClient.prefetchQuery(sessionsQuery(teacherId, nearRange));
    }
  }, [range.from, isAdmin, teacherId, queryClient]);

  const week = sessions.data ?? [];
  const visible = week
    .filter((s) => s.sessionDate === day)
    .sort((a, b) => (a.startsAt ?? "").localeCompare(b.startsAt ?? ""));
  const setDay = (key: string) => setParams({ day: key }, { replace: true });
  const shiftWeek = (weeks: number) => {
    setDirection(weeks > 0 ? "right" : "left");
    const next = parseDateOnly(day);
    next.setDate(next.getDate() + 7 * weeks);
    setDay(dateKey(next));
  };

  return (
    <>
      <PageHeader eyebrow={formatDateLong(new Date())} title="Yoklama" />
      {isAdmin && (
        <div className="mt-4 flex flex-wrap gap-2">
          <PillButton onClick={() => setCreating(!creating)}>Ek ders aç</PillButton>
          <PillLink to="/schedules">Haftalık program</PillLink>
          <PillLink to="/excuses">Mazeretler</PillLink>
        </div>
      )}
      {isAdmin && creating && (
        <div className="mt-4">
          <NewSessionForm day={day} onDone={() => setCreating(false)} />
        </div>
      )}
      <div className="mt-6 grid items-start gap-4 lg:grid-cols-[360px_minmax(0,1fr)]">
        <Card className="p-5">
          <CardHeader
            title={weekRangeLabel(days)}
            action={
              <span className="flex gap-1">
                <button
                  type="button"
                  aria-label="Önceki hafta"
                  onClick={() => shiftWeek(-1)}
                  className="grid size-8 place-items-center rounded-full hover:bg-sub"
                >
                  <ChevronLeft size={16} strokeWidth={1.5} aria-hidden="true" />
                </button>
                <button
                  type="button"
                  aria-label="Sonraki hafta"
                  onClick={() => shiftWeek(1)}
                  className="grid size-8 place-items-center rounded-full hover:bg-sub"
                >
                  <ChevronRight size={16} strokeWidth={1.5} aria-hidden="true" />
                </button>
              </span>
            }
          />
          <div
            key={range.from}
            className={cn(
              "mt-5",
              direction === "right" && "anim-slide-from-right",
              direction === "left" && "anim-slide-from-left",
            )}
          >
            <WeekStrip
              days={days}
              selected={day}
              marked={new Set(week.map((s) => s.sessionDate ?? ""))}
              label={`${weekRangeLabel(days)} haftası`}
              markedText="ders var"
              onSelect={setDay}
            />
          </div>
        </Card>

        <Card className="overflow-hidden">
          <CardHeader
            title={formatFullDateWithWeekday(parseDateOnly(day))}
            className="px-5 pt-5 pb-4"
          />
          <TableHead cols={cols}>
            <Th>Saat</Th>
            <Th className="hidden md:flex">Sınıf</Th>
            {isAdmin && <Th className="hidden md:flex">Öğretmen</Th>}
            <Th className="hidden md:flex">Durum</Th>
            <Th className="hidden md:flex" />
          </TableHead>
          {sessions.isError ? (
            <QueryError error={sessions.error} />
          ) : sessions.isPending ? (
            <div className="pt-4">
              <SkeletonRows rows={3} />
            </div>
          ) : visible.length === 0 ? (
            <EmptyState
              icon={CalendarX}
              title="Bu gün ders yok"
              text={
                isAdmin
                  ? "Seçilen günde şubede planlanmış bir ders görünmüyor."
                  : "Seçtiğin günde sana atanmış bir ders görünmüyor."
              }
            />
          ) : (
            visible.map((session) => {
              const badge = sessionBadge(session.status!);
              const hours = `${formatClock(session.startsAt)} – ${formatClock(session.endsAt)}`;
              const classroom = classroomNames.get(session.classroomId) ?? "Sınıf";
              return (
                <TableRow key={session.id} cols={cols} className="anim-fade">
                  <Td>
                    <div className="text-sm font-medium">{hours}</div>
                    <div className="text-[13px] text-dim md:hidden">{classroom}</div>
                  </Td>
                  <Td className="hidden text-sm md:block">{classroom}</Td>
                  {isAdmin && (
                    <Td className="hidden text-sm md:block">
                      {teacherNames.get(session.teacherId) ?? "Öğretmen"}
                    </Td>
                  )}
                  <Td className="hidden md:block">
                    <StatusBadge tone={badge.tone} label={badge.label} />
                  </Td>
                  <Td className="flex items-center justify-end gap-2">
                    <span className="md:hidden">
                      <StatusBadge tone={badge.tone} label={badge.label} iconOnly />
                    </span>
                    <PillLink to={`/sessions/${session.id}`}>
                      {session.status === "PLANNED" || (isAdmin && session.status === "EXPIRED")
                        ? "Yoklama al"
                        : "Görüntüle"}
                    </PillLink>
                  </Td>
                </TableRow>
              );
            })
          )}
        </Card>
      </div>
    </>
  );
}
