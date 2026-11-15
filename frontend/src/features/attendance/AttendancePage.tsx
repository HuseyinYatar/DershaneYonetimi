import { CalendarCheck, CalendarDays, CalendarX, Clock, UserX } from "lucide-react";
import { useSearchParams } from "react-router";
import { useAttendanceRecords, useAttendanceSummary } from "@/api/queries/attendance";
import { useUserSubjectNames } from "@/api/queries/user";
import { useActor } from "@/auth/useActor";
import { AttendanceLegend } from "@/components/AttendanceLegend";
import { StatusBadge } from "@/components/StatusBadge";
import { SemicircleGauge } from "@/components/charts/SemicircleGauge";
import { Card, CardHeader } from "@/components/ui/Card";
import { EmptyState, QueryError } from "@/components/ui/EmptyState";
import { MonthPicker } from "@/components/ui/MonthPicker";
import { PageHeader } from "@/components/ui/PageHeader";
import { PillButton } from "@/components/ui/Pill";
import { GaugeSkeleton } from "@/components/ui/ChartSkeletons";
import { SkeletonRows } from "@/components/ui/Skeleton";
import { StatStrip } from "@/components/ui/StatStrip";
import { TableHead, TableRow, Td, Th } from "@/components/ui/Table";
import { attendanceRate, attendanceSegments } from "@/lib/attendance";
import {
  formatClock,
  formatDateLong,
  formatFullDateWithWeekday,
  formatInt,
  formatMonthYear,
  formatPercent,
  parseDateOnly,
} from "@/lib/format";
import { attendanceBadge } from "@/lib/status/attendance";
import { dateKey } from "@/lib/week";

const COLS =
  "grid-cols-[minmax(0,1fr)_auto] sm:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_120px_130px]";
const MONTH = /^\d{4}-(0[1-9]|1[0-2])$/;

// "2026-11" -> first and last day of the month as API dates.
function monthRange(month: string) {
  const [y, m] = month.split("-").map(Number);
  return { from: dateKey(new Date(y, m - 1, 1)), to: dateKey(new Date(y, m, 0)) };
}

export function AttendancePage() {
  const actor = useActor();
  const [params, setParams] = useSearchParams();
  const monthParam = params.get("month");
  const month = monthParam && MONTH.test(monthParam) ? monthParam : undefined;
  const range = month ? monthRange(month) : {};

  const summary = useAttendanceSummary(actor?.id, range);
  const records = useAttendanceRecords(actor?.id, range);
  const subjectNames = useUserSubjectNames().data;
  const rate = attendanceRate(summary.data);
  const setMonth = (value: string | undefined) =>
    setParams(value ? { month: value } : {}, { replace: true });

  return (
    <>
      <PageHeader eyebrow={formatDateLong(new Date())} title="Yoklama" />
      <div className="mt-6">
        <StatStrip
          stats={[
            {
              icon: CalendarDays,
              value: summary.data ? formatInt(summary.data.total ?? 0) : "—",
              label: "ders",
            },
            {
              icon: CalendarCheck,
              value: rate === null ? "—" : formatPercent(rate),
              label: "katılım",
            },
            {
              icon: UserX,
              value: summary.data ? formatInt(summary.data.absent ?? 0) : "—",
              label: "devamsızlık",
            },
            {
              icon: Clock,
              value: summary.data ? formatInt(summary.data.late ?? 0) : "—",
              label: "geç kalma",
            },
          ]}
        />
      </div>

      <div className="mt-4 flex items-center gap-2">
        <MonthPicker value={month} onChange={setMonth} />
        {month && <PillButton onClick={() => setMonth(undefined)}>Tüm kayıtlar</PillButton>}
      </div>

      <div className="mt-4 grid items-start gap-4 lg:grid-cols-[360px_minmax(0,1fr)]">
        <Card className="p-5">
          <CardHeader
            title="Katılım"
            action={
              <span className="text-[13px] text-dim">
                {month ? formatMonthYear(parseDateOnly(`${month}-01`)) : "Tüm dersler"}
              </span>
            }
          />
          {summary.isError ? (
            <QueryError error={summary.error} />
          ) : summary.data ? (
            <>
              <div className="mt-5">
                <SemicircleGauge
                  rate={rate}
                  segments={attendanceSegments(summary.data)}
                  caption={rate === null ? "henüz yoklama yok" : "katılım oranı"}
                  ariaLabel={
                    rate === null
                      ? "Katılım: yoklama kaydı yok"
                      : `Katılım yüzde ${rate}: ${summary.data.present ?? 0} var, ${summary.data.late ?? 0} geç, ${summary.data.absent ?? 0} yok, ${summary.data.excused ?? 0} izinli`
                  }
                />
              </div>
              <div className="mt-6">
                <AttendanceLegend summary={summary.data} />
              </div>
            </>
          ) : (
            <div className="mt-5">
              <GaugeSkeleton />
            </div>
          )}
        </Card>

        <Card className="overflow-hidden">
          <CardHeader title="Yoklama kayıtları" className="px-5 pt-5 pb-4" />
          <TableHead cols={COLS}>
            <Th>Tarih</Th>
            <Th className="hidden sm:flex">Ders</Th>
            <Th className="hidden sm:flex">Saat</Th>
            <Th>Durum</Th>
          </TableHead>
          {records.isError ? (
            <QueryError error={records.error} />
          ) : records.isPending ? (
            <div className="pt-4">
              <SkeletonRows rows={5} />
            </div>
          ) : (records.data ?? []).length === 0 ? (
            <EmptyState
              icon={CalendarX}
              title="Yoklama kaydı yok"
              text={
                month
                  ? `${formatMonthYear(parseDateOnly(`${month}-01`))} ayında yoklama alınmış ders görünmüyor.`
                  : "Henüz yoklama alınmış bir ders yok."
              }
              action={
                month && <PillButton onClick={() => setMonth(undefined)}>Tüm kayıtlar</PillButton>
              }
            />
          ) : (
            (records.data ?? []).map((record) => {
              const badge = attendanceBadge(record.status!);
              const subject = (record.subjectId && subjectNames?.get(record.subjectId)) || "Ders";
              const hours = `${formatClock(record.startsAt)} – ${formatClock(record.endsAt)}`;
              return (
                <TableRow key={record.id} cols={COLS}>
                  <Td>
                    <div className="text-sm font-medium">
                      {record.sessionDate
                        ? formatFullDateWithWeekday(parseDateOnly(record.sessionDate))
                        : "—"}
                    </div>
                    <div className="text-[13px] text-dim sm:hidden">
                      {subject} · {hours}
                    </div>
                  </Td>
                  <Td className="hidden text-sm sm:block">{subject}</Td>
                  <Td className="hidden text-sm sm:block">{hours}</Td>
                  <Td>
                    <StatusBadge tone={badge.tone} label={badge.label} />
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
