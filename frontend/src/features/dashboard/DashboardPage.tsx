import { CalendarCheck, ClipboardList, Target } from "lucide-react";
import { useParams } from "react-router";
import { useAttendanceSummary } from "@/api/queries/attendance";
import { useStudentResults } from "@/api/queries/exam";
import { useAssignments } from "@/api/queries/task";
import { useMe } from "@/api/queries/user";
import { PageHeader } from "@/components/ui/PageHeader";
import { StatStrip } from "@/components/ui/StatStrip";
import { attendanceRate } from "@/lib/attendance";
import { countByStatus } from "@/lib/assignments";
import { formatDateLong, formatNet, formatPercent, greetingFor } from "@/lib/format";
import { AttendanceCard } from "./AttendanceCard";
import { LastExamCard } from "./LastExamCard";
import { ProgressCard } from "./ProgressCard";
import { TasksCard } from "./TasksCard";
import { WeakTopicsCard } from "./WeakTopicsCard";
import { WeekCard } from "./WeekCard";

export function DashboardPage() {
  const { studentId = "" } = useParams();
  const me = useMe();
  const assignments = useAssignments(studentId);
  const results = useStudentResults(studentId);
  const summary = useAttendanceSummary(studentId);

  const now = new Date();
  const greeting = greetingFor(now.getHours());
  const rate = attendanceRate(summary.data);
  const lastNet = results.data?.[0]?.net;

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
              icon: ClipboardList,
              value: assignments.data ? String(countByStatus(assignments.data).waiting) : "—",
              label: "bekleyen görev",
            },
            { icon: Target, value: lastNet ? formatNet(lastNet) : "—", label: "son net" },
            {
              icon: CalendarCheck,
              value: rate === null ? "—" : formatPercent(rate),
              label: "katılım",
            },
          ]}
        />
      </div>
      <div className="mt-5 grid gap-4 lg:grid-cols-2 wide:grid-cols-3">
        <LastExamCard studentId={studentId} />
        <AttendanceCard studentId={studentId} />
        <WeakTopicsCard studentId={studentId} />
        <ProgressCard studentId={studentId} />
        <TasksCard studentId={studentId} />
        <WeekCard studentId={studentId} />
      </div>
    </>
  );
}
