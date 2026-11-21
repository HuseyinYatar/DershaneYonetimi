import { FileText, Target, TrendingUp } from "lucide-react";
import { useStudentResults, useStudentTytResults } from "@/api/queries/exam";
import { useStudent } from "@/api/queries/user";
import { Breadcrumb, PageHeader } from "@/components/ui/PageHeader";
import { Skeleton } from "@/components/ui/Skeleton";
import { StatStrip } from "@/components/ui/StatStrip";
import { formatInt, formatNet } from "@/lib/format";
import { LastExamCard } from "./LastExamCard";
import { ProgressCard } from "./ProgressCard";
import { WeakTopicsCard } from "./WeakTopicsCard";

const BACK = { label: "Sınıflarım", to: "/classrooms" };

// What a teacher sees of a student: name and exam results only.
export function StudentExamView({ studentId }: { studentId: string }) {
  const student = useStudent(studentId);
  const results = useStudentResults(studentId);
  // Last and best net compare TYT with TYT; the exam count includes every type.
  const tyt = useStudentTytResults(studentId);

  const name = [student.data?.firstName, student.data?.lastName].filter(Boolean).join(" ");
  const nets = (tyt.data ?? []).map((r) => Number(r.net));
  const best = nets.length > 0 ? Math.max(...nets) : undefined;

  return (
    <>
      <PageHeader
        breadcrumb={<Breadcrumb parent={BACK} current={name || "Öğrenci"} />}
        eyebrow={
          student.data?.studentNumber ? `Öğrenci no ${student.data.studentNumber}` : undefined
        }
        title={
          name ||
          (student.isPending ? (
            <Skeleton className="mt-2 h-9 w-72 max-w-full rounded-full md:h-10" />
          ) : (
            "Öğrenci"
          ))
        }
      />
      <div className="mt-6">
        <StatStrip
          stats={[
            {
              icon: FileText,
              value: results.data ? formatInt(results.data.length) : "—",
              label: "açıklanmış deneme",
            },
            { icon: Target, value: formatNet(tyt.data?.[0]?.net), label: "son net" },
            { icon: TrendingUp, value: formatNet(best), label: "en yüksek net" },
          ]}
        />
      </div>
      <div className="mt-5 grid gap-4 lg:grid-cols-2 wide:grid-cols-3">
        <LastExamCard studentId={studentId} />
        <WeakTopicsCard studentId={studentId} />
        <ProgressCard studentId={studentId} />
      </div>
    </>
  );
}
