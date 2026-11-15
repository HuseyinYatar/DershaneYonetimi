import { CalendarCheck, CalendarDays, ClipboardCheck, School } from "lucide-react";
import { useSessions } from "@/api/queries/attendance";
import { useSubmittedAssignments } from "@/api/queries/task";
import { useMe, useMyClassrooms, useMyStudentNames } from "@/api/queries/user";
import { useActor } from "@/auth/useActor";
import { PageHeader } from "@/components/ui/PageHeader";
import { StatStrip } from "@/components/ui/StatStrip";
import { formatDateLong, formatInt, greetingFor } from "@/lib/format";
import { dateKey } from "@/lib/week";
import { PendingGradingCard } from "./PendingGradingCard";
import { TodayLessonsCard } from "./TodayLessonsCard";

const PENDING_ROWS = 5;

export function TeacherOverviewPage() {
  const actor = useActor();
  const me = useMe();
  const now = new Date();
  const today = dateKey(now);
  const sessions = useSessions(actor?.id, { from: today, to: today });
  const classrooms = useMyClassrooms();
  const submitted = useSubmittedAssignments(PENDING_ROWS);
  const studentNames = useMyStudentNames();

  const greeting = greetingFor(now.getHours());
  const classroomNames = new Map((classrooms.data ?? []).map((c) => [c.id, c.name]));
  const todaySessions = sessions.data ?? [];

  return (
    <>
      <PageHeader
        eyebrow={formatDateLong(now)}
        title={me.data?.firstName ? `${greeting}, ${me.data.firstName}!` : `${greeting}!`}
      />
      <div className="mt-6">
        <StatStrip
          stats={[
            {
              icon: CalendarDays,
              value: sessions.data ? formatInt(todaySessions.length) : "—",
              label: "bugünkü ders",
            },
            {
              icon: CalendarCheck,
              value: sessions.data
                ? formatInt(todaySessions.filter((s) => s.status === "PLANNED").length)
                : "—",
              label: "yoklama bekleyen",
            },
            {
              icon: ClipboardCheck,
              value: submitted.data ? formatInt(submitted.data.page?.totalElements ?? 0) : "—",
              // Short on purpose: "değerlendirme" is wider than a phone stat cell.
              label: "not bekleyen",
            },
            {
              icon: School,
              value: classrooms.data ? formatInt(classrooms.data.length) : "—",
              label: "sınıf",
            },
          ]}
        />
      </div>
      <div className="mt-5 grid items-start gap-4 lg:grid-cols-2">
        <TodayLessonsCard
          sessions={todaySessions}
          isPending={sessions.isPending}
          error={sessions.error}
          classroomNames={classroomNames}
        />
        <PendingGradingCard
          rows={submitted.data?.content ?? []}
          isPending={submitted.isPending}
          error={submitted.error}
          studentNames={studentNames}
        />
      </div>
    </>
  );
}
