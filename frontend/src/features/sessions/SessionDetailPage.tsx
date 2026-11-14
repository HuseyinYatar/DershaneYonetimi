import { useParams } from "react-router";
import { useRoster, useSession } from "@/api/queries/attendance";
import { useMyClassrooms } from "@/api/queries/user";
import { StatusBadge } from "@/components/StatusBadge";
import { Card } from "@/components/ui/Card";
import { EmptyState, QueryError } from "@/components/ui/EmptyState";
import { Breadcrumb, PageHeader } from "@/components/ui/PageHeader";
import { Skeleton } from "@/components/ui/Skeleton";
import { formatClock, formatFullDateWithWeekday, parseDateOnly } from "@/lib/format";
import { sessionBadge } from "@/lib/status/sessions";
import { CompletedAttendance } from "./CompletedAttendance";
import { TakeAttendance, TakeAttendanceSkeleton } from "./TakeAttendance";

const BACK = { label: "Yoklama", to: "/sessions" };

export function SessionDetailPage() {
  const { sessionId = "" } = useParams();
  const session = useSession(sessionId);
  const status = session.data?.status;
  const roster = useRoster(sessionId, status === "PLANNED" || status === "COMPLETED");
  const classrooms = useMyClassrooms();

  if (session.isError) {
    return (
      <>
        <PageHeader breadcrumb={<Breadcrumb parent={BACK} current="Ders" />} title="Ders" />
        <QueryError error={session.error} className="py-16" />
      </>
    );
  }
  const data = session.data;
  if (!data) {
    return (
      <>
        <PageHeader breadcrumb={<Breadcrumb parent={BACK} current="…" />} title="Ders" />
        <div role="status" aria-label="Yükleniyor" className="mt-6 flex flex-col gap-4">
          <Skeleton className="h-12" />
          <Skeleton className="h-64" />
        </div>
      </>
    );
  }

  const classroom = classrooms.data?.find((c) => c.id === data.classroomId)?.name ?? "Ders";
  const badge = sessionBadge(data.status!);
  const students = roster.data?.students ?? [];

  const body = () => {
    if (data.status === "CANCELLED" || data.status === "EXPIRED") {
      return (
        <Card>
          <EmptyState
            text={
              data.status === "CANCELLED"
                ? "Bu ders iptal edildi, yoklama alınmaz."
                : "Bu dersin günü bitti ve yoklama alınmadı. Yoklamayı yalnızca şube yöneticisi alabilir."
            }
          />
        </Card>
      );
    }
    if (roster.isError) return <QueryError error={roster.error} />;
    if (roster.isPending) return <TakeAttendanceSkeleton />;
    return data.status === "PLANNED" ? (
      <TakeAttendance sessionId={sessionId} students={students} />
    ) : (
      <CompletedAttendance sessionId={sessionId} students={students} />
    );
  };

  return (
    <>
      <PageHeader
        breadcrumb={<Breadcrumb parent={BACK} current={classroom} />}
        eyebrow={`${formatFullDateWithWeekday(parseDateOnly(data.sessionDate ?? ""))} · ${formatClock(data.startsAt)} – ${formatClock(data.endsAt)}`}
        title={classroom}
      />
      <div className="mt-4">
        <StatusBadge tone={badge.tone} label={badge.label} />
      </div>
      <div className="mt-5">{body()}</div>
    </>
  );
}
