import { Link } from "react-router";
import type { components } from "@/api/schema/attendance";
import { StatusBadge } from "@/components/StatusBadge";
import { Card, CardHeader } from "@/components/ui/Card";
import { EmptyState, QueryError } from "@/components/ui/EmptyState";
import { PillLink } from "@/components/ui/Pill";
import { SkeletonRows } from "@/components/ui/Skeleton";
import { formatClock } from "@/lib/format";
import { sessionBadge } from "@/lib/status/sessions";

type Session = components["schemas"]["SessionResponse"];

export function TodayLessonsCard({
  sessions,
  isPending,
  error,
  classroomNames,
}: {
  sessions: Session[];
  isPending: boolean;
  error: unknown;
  classroomNames: Map<string | undefined, string | undefined>;
}) {
  const rows = [...sessions].sort((a, b) => (a.startsAt ?? "").localeCompare(b.startsAt ?? ""));

  return (
    <Card className="overflow-hidden">
      <CardHeader
        title="Bugünkü derslerim"
        className="px-5 pt-5 pb-4"
        action={<PillLink to="/sessions">Tümünü gör</PillLink>}
      />
      {error ? (
        <QueryError error={error} />
      ) : isPending ? (
        <SkeletonRows rows={3} />
      ) : rows.length === 0 ? (
        <EmptyState text="Bugün dersin yok." />
      ) : (
        rows.map((session) => {
          const badge = sessionBadge(session.status!);
          return (
            <div
              key={session.id}
              className="flex items-center justify-between gap-3 border-t border-line px-5 py-4"
            >
              <div className="min-w-0">
                <div className="text-sm font-medium">
                  {formatClock(session.startsAt)} – {formatClock(session.endsAt)}
                </div>
                <div className="truncate text-[13px] text-dim">
                  {classroomNames.get(session.classroomId) ?? "Sınıf"}
                </div>
              </div>
              <div className="flex shrink-0 items-center gap-2">
                <span className="hidden sm:inline-flex">
                  <StatusBadge tone={badge.tone} label={badge.label} />
                </span>
                <span className="sm:hidden">
                  <StatusBadge tone={badge.tone} label={badge.label} iconOnly />
                </span>
                <Link
                  to={`/sessions/${session.id}`}
                  className="inline-flex h-8 items-center rounded-full border border-line bg-white px-3 text-[13px] font-medium hover:border-[#cfd2d9]"
                >
                  {session.status === "PLANNED" ? "Yoklama al" : "Görüntüle"}
                </Link>
              </div>
            </div>
          );
        })
      )}
    </Card>
  );
}
