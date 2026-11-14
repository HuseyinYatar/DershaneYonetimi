import { CalendarX, ChevronLeft, ChevronRight } from "lucide-react";
import { useSearchParams } from "react-router";
import { useSessions } from "@/api/queries/attendance";
import { useMyClassrooms } from "@/api/queries/user";
import { useActor } from "@/auth/useActor";
import { StatusBadge } from "@/components/StatusBadge";
import { WeekStrip } from "@/components/WeekStrip";
import { Card, CardHeader } from "@/components/ui/Card";
import { EmptyState, QueryError } from "@/components/ui/EmptyState";
import { PageHeader } from "@/components/ui/PageHeader";
import { PillLink } from "@/components/ui/Pill";
import { SkeletonRows } from "@/components/ui/Skeleton";
import { TableHead, TableRow, Td, Th } from "@/components/ui/Table";
import {
  formatClock,
  formatDateLong,
  formatFullDateWithWeekday,
  parseDateOnly,
} from "@/lib/format";
import { sessionBadge } from "@/lib/status/sessions";
import { dateKey, weekDays, weekRangeLabel } from "@/lib/week";

const COLS = "grid-cols-[minmax(0,1fr)_auto] md:grid-cols-[130px_minmax(0,1fr)_170px_120px]";
const DAY = /^\d{4}-\d{2}-\d{2}$/;

export function SessionsPage() {
  const actor = useActor();
  const [params, setParams] = useSearchParams();
  const dayParam = params.get("day");
  const day = dayParam && DAY.test(dayParam) ? dayParam : dateKey(new Date());
  const days = weekDays(parseDateOnly(day));
  const sessions = useSessions(actor?.id, { from: dateKey(days[0]), to: dateKey(days[6]) });
  const classrooms = useMyClassrooms();
  const classroomNames = new Map((classrooms.data ?? []).map((c) => [c.id, c.name]));

  const week = sessions.data ?? [];
  const visible = week
    .filter((s) => s.sessionDate === day)
    .sort((a, b) => (a.startsAt ?? "").localeCompare(b.startsAt ?? ""));
  const setDay = (key: string) => setParams({ day: key }, { replace: true });
  const shiftWeek = (weeks: number) => {
    const next = parseDateOnly(day);
    next.setDate(next.getDate() + 7 * weeks);
    setDay(dateKey(next));
  };

  return (
    <>
      <PageHeader eyebrow={formatDateLong(new Date())} title="Yoklama" />
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
          <div className="mt-5">
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
          <TableHead cols={COLS}>
            <Th>Saat</Th>
            <Th className="hidden md:flex">Sınıf</Th>
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
              text="Seçtiğin günde sana atanmış bir ders görünmüyor."
            />
          ) : (
            visible.map((session) => {
              const badge = sessionBadge(session.status!);
              const hours = `${formatClock(session.startsAt)} – ${formatClock(session.endsAt)}`;
              const classroom = classroomNames.get(session.classroomId) ?? "Sınıf";
              return (
                <TableRow key={session.id} cols={COLS}>
                  <Td>
                    <div className="text-sm font-medium">{hours}</div>
                    <div className="text-[13px] text-dim md:hidden">{classroom}</div>
                  </Td>
                  <Td className="hidden text-sm md:block">{classroom}</Td>
                  <Td className="hidden md:block">
                    <StatusBadge tone={badge.tone} label={badge.label} />
                  </Td>
                  <Td className="flex items-center justify-end gap-2">
                    <span className="md:hidden">
                      <StatusBadge tone={badge.tone} label={badge.label} iconOnly />
                    </span>
                    <PillLink to={`/sessions/${session.id}`}>
                      {session.status === "PLANNED" ? "Yoklama al" : "Görüntüle"}
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
