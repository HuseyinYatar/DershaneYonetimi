import { useAssignment, useAssignments } from "@/api/queries/task";
import { useActor } from "@/auth/useActor";
import { StatusBadge } from "@/components/StatusBadge";
import { WeekStrip } from "@/components/WeekStrip";
import { Card, CardHeader } from "@/components/ui/Card";
import { EmptyState, QueryError } from "@/components/ui/EmptyState";
import { PageHeader } from "@/components/ui/PageHeader";
import { SkeletonRows } from "@/components/ui/Skeleton";
import { StatStrip } from "@/components/ui/StatStrip";
import { TableHead, TableRow, Td, Th } from "@/components/ui/Table";
import { countByStatus, deadlineDayKeys, type Assignment } from "@/lib/assignments";
import { formatDateLong, formatDeadline, formatRelativeDeadline } from "@/lib/format";
import { assignmentBadge } from "@/lib/status/tasks";
import { dateKey, weekDays, weekRangeLabel } from "@/lib/week";
import {
  Calendar,
  CircleAlert,
  CircleCheck,
  Clock,
  FileText,
  LoaderCircle,
  Star,
} from "lucide-react";
import { Link, useSearchParams } from "react-router";
import { StatusFilter } from "./StatusFilter";

const COLS = "grid-cols-[minmax(0,1fr)_auto] md:grid-cols-[minmax(0,1fr)_130px_170px_90px]";
const STATUSES = ["ASSIGNED", "OVERDUE", "SUBMITTED", "GRADED", "CANCELLED"] as const;
// Only the first rows load their detail (score, feedback, late flag); keeps the request count under the gateway rate limit.
const DETAIL_ROWS = 30;

function Row({ row, withDetail }: { row: Assignment; withDetail: boolean }) {
  const needsDetail = withDetail && (row.status === "SUBMITTED" || row.status === "GRADED");
  const detail = useAssignment(row.id, needsDetail).data;
  const badge = assignmentBadge(row.status!);
  const due = row.dueAt ? formatDeadline(new Date(row.dueAt)) : "—";
  const score = detail?.evaluation?.score;
  const feedback = detail?.evaluation?.feedback;

  return (
    <TableRow cols={COLS} className="relative items-start gap-y-1 hover:bg-surface">
      <Td>
        <Link
          to={`/tasks/${row.id}`}
          className="block text-sm leading-5 font-medium after:absolute after:inset-0 hover:underline"
        >
          {row.taskTitle}
        </Link>
        <div className="text-[13px] text-dim md:hidden">
          {due}
          {score !== undefined && ` · ${score}/100`}
        </div>
        {feedback && (
          <div className="mt-1 text-[13px] leading-snug text-dim">Öğretmen: “{feedback}”</div>
        )}
      </Td>
      <Td divider className="hidden text-sm leading-5 md:block">
        {due}
      </Td>
      <Td divider className="-mt-1.5 flex flex-col items-start gap-1">
        <span className="md:hidden">
          <StatusBadge tone={badge.tone} label={badge.label} iconOnly />
        </span>
        <span className="hidden md:inline-flex">
          <StatusBadge tone={badge.tone} label={badge.label} />
        </span>
        {detail?.submission?.late && (
          <span className="hidden text-[13px] text-dim md:inline">geç teslim</span>
        )}
      </Td>
      <Td divider className="hidden text-sm leading-5 md:block">
        {score === undefined ? "—" : `${score}/100`}
      </Td>
    </TableRow>
  );
}

export function TasksPage() {
  const actor = useActor();
  const assignments = useAssignments(actor?.id);
  const [params, setParams] = useSearchParams();

  const statusParam = params.get("status");
  const status = STATUSES.find((s) => s === statusParam);
  const day = params.get("day") ?? undefined;
  const setParam = (key: string, value: string | undefined) =>
    setParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        if (value) next.set(key, value);
        else next.delete(key);
        return next;
      },
      { replace: true },
    );

  const now = new Date();
  const days = weekDays(now);
  const all = assignments.data ?? [];
  const counts = countByStatus(all);
  const visible = all.filter(
    (a) =>
      (!status || a.status === status) && (!day || (a.dueAt && dateKey(new Date(a.dueAt)) === day)),
  );
  const soon = all
    .filter((a) => a.status === "ASSIGNED" && a.dueAt)
    .sort((a, b) => a.dueAt!.localeCompare(b.dueAt!))
    .slice(0, 3);

  return (
    <>
      <PageHeader eyebrow={formatDateLong(now)} title="Görevlerim" />
      <div className="mt-6">
        <StatStrip
          stats={[
            { icon: Clock, value: String(counts.waiting), label: "bekliyor" },
            { icon: CircleAlert, value: String(counts.overdue), label: "gecikmiş" },
            { icon: CircleCheck, value: String(counts.done), label: "tamamlanan" },
          ]}
        />
      </div>
      <div className="mt-4">
        <StatusFilter
          statuses={STATUSES}
          value={status}
          onChange={(next) => setParam("status", next)}
        />
      </div>

      <div className="mt-4 grid items-start gap-4 lg:grid-cols-[minmax(0,1fr)_360px]">
        <Card className="overflow-hidden">
          <CardHeader
            className="px-5 pt-5 pb-4"
            title={
              <>
                Tüm görevler <span className="font-normal text-dim">{visible.length}</span>
              </>
            }
          />
          <TableHead cols={COLS}>
            <Th icon={FileText}>Görev</Th>
            <Th icon={Calendar} divider className="hidden md:flex">
              Son teslim
            </Th>
            <Th icon={LoaderCircle} divider>
              Durum
            </Th>
            <Th icon={Star} divider className="hidden md:flex">
              Not
            </Th>
          </TableHead>
          {assignments.isError ? (
            <QueryError error={assignments.error} />
          ) : assignments.isPending ? (
            <div className="pt-4">
              <SkeletonRows rows={5} />
            </div>
          ) : visible.length === 0 ? (
            <EmptyState
              text={all.length === 0 ? "Henüz görevin yok." : "Bu süzgece uyan görev yok."}
            />
          ) : (
            visible.map((row, i) => <Row key={row.id} row={row} withDetail={i < DETAIL_ROWS} />)
          )}
        </Card>

        <Card className="p-5">
          <CardHeader
            title="Bu hafta"
            icon={<Calendar size={18} strokeWidth={1.5} aria-hidden="true" />}
            action={
              <span className="flex items-center gap-3 text-[13px] text-dim">
                {weekRangeLabel(days)}
                {day && (
                  <button
                    type="button"
                    onClick={() => setParam("day", undefined)}
                    className="font-medium text-brand-ink hover:underline"
                  >
                    Tüm hafta
                  </button>
                )}
              </span>
            }
          />
          <div className="mt-5">
            <WeekStrip
              days={days}
              selected={day}
              marked={deadlineDayKeys(all, days)}
              label={`${weekRangeLabel(days)} haftası`}
              onSelect={(key) => setParam("day", key === day ? undefined : key)}
            />
          </div>
          <h3 className="mt-6 text-[13px] text-dim">Yaklaşan son teslimler</h3>
          {soon.length === 0 ? (
            <p className="mt-3 text-[13px] text-dim">Yaklaşan son teslim yok.</p>
          ) : (
            <ul className="mt-3 flex flex-col gap-4">
              {soon.map((row) => (
                <li key={row.id} className="flex gap-3">
                  <span className="w-[3px] rounded-full bg-brand" />
                  <div className="min-w-0">
                    <Link
                      to={`/tasks/${row.id}`}
                      className="text-sm leading-snug font-medium hover:underline"
                    >
                      {row.taskTitle}
                    </Link>
                    <div className="mt-0.5 text-[13px] text-dim">
                      {formatRelativeDeadline(new Date(row.dueAt!), now)}
                    </div>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>
    </>
  );
}
