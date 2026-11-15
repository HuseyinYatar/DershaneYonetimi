import { AlarmClock, Calendar } from "lucide-react";
import { Link } from "react-router";
import { useAssignments } from "@/api/queries/task";
import { WeekStrip } from "@/components/WeekStrip";
import { Card, CardHeader } from "@/components/ui/Card";
import { deadlineDayKeys, deadlinesOn } from "@/lib/assignments";
import { formatDeadline, formatTime } from "@/lib/format";
import { dateKey, weekDays, weekRangeLabel } from "@/lib/week";

// shortcut: only assignment deadlines. No endpoint lets a student list their own class sessions
// (attendance sessions are staff-only), so lessons are not shown.
export function WeekCard({ studentId }: { studentId: string }) {
  const assignments = useAssignments(studentId);
  const today = new Date();
  const days = weekDays(today);
  const list = assignments.data ?? [];
  const deadlines = deadlinesOn(list, days);

  return (
    <Card className="p-5 lg:col-span-2 wide:col-span-1 wide:col-start-3 wide:row-start-2">
      <CardHeader
        title="Bu hafta"
        icon={<Calendar size={18} strokeWidth={1.5} aria-hidden="true" />}
        action={<span className="text-[13px] text-dim">{weekRangeLabel(days)}</span>}
      />
      <div className="mt-5">
        <WeekStrip
          days={days}
          selected={dateKey(today)}
          marked={deadlineDayKeys(list, days)}
          label={`${weekRangeLabel(days)} haftası`}
        />
      </div>
      {deadlines.length === 0 ? (
        <p className="mt-6 text-[13px] text-dim">
          {assignments.isPending ? "Yükleniyor…" : "Bu hafta son teslim yok."}
        </p>
      ) : (
        <ul className="mt-6 flex flex-col gap-6">
          {deadlines.map((row) => {
            const due = new Date(row.dueAt!);
            const isToday = dateKey(due) === dateKey(today);
            return (
              <li key={row.id} className="flex gap-3">
                <span className="w-[3px] rounded-full bg-brand" />
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2 text-sm leading-snug font-medium">
                    <AlarmClock
                      size={16}
                      strokeWidth={1.5}
                      aria-hidden="true"
                      className="shrink-0 text-dim"
                    />
                    <Link to={`/tasks/${row.id}`} className="min-w-0 hover:underline">
                      {row.taskTitle}
                    </Link>
                  </div>
                  <div className="mt-1 pl-6 text-[13px] text-dim">
                    Son teslim {isToday ? formatTime(due) : formatDeadline(due)}
                  </div>
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </Card>
  );
}
