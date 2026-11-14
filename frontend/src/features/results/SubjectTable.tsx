import { BookOpen, ChartNoAxesColumn } from "lucide-react";
import type { components } from "@/api/schema/exam-analysis";
import { Card, CardTitle } from "@/components/ui/Card";
import { Skeleton } from "@/components/ui/Skeleton";
import { TableHead, TableRow, Td, Th } from "@/components/ui/Table";
import { formatInt, formatNet, formatPercent } from "@/lib/format";

type Subject = components["schemas"]["SubjectResultRead"];

const COLS =
  "grid-cols-[minmax(0,1fr)_34px_34px_34px_56px] sm:grid-cols-[minmax(0,1fr)_56px_56px_56px_80px_190px]";

function Head() {
  return (
    <TableHead cols={COLS}>
      <Th icon={BookOpen}>Ders</Th>
      <Th>
        <span className="sm:hidden">D</span>
        <span className="hidden sm:inline">Doğru</span>
      </Th>
      <Th>
        <span className="sm:hidden">Y</span>
        <span className="hidden sm:inline">Yanlış</span>
      </Th>
      <Th>
        <span className="sm:hidden">B</span>
        <span className="hidden sm:inline">Boş</span>
      </Th>
      <Th>Net</Th>
      <Th icon={ChartNoAxesColumn} divider className="hidden sm:flex">
        Başarı
      </Th>
    </TableHead>
  );
}

// Real title and column heads, grey placeholder rows with the same grid.
export function SubjectTableSkeleton({ className }: { className?: string }) {
  return (
    <Card className={`overflow-hidden ${className ?? ""}`}>
      <div className="px-5 pt-5 pb-4">
        <CardTitle>Ders sonuçları</CardTitle>
      </div>
      <Head />
      {[0, 1, 2, 3].map((i) => (
        <TableRow key={i} cols={COLS}>
          <Td className="space-y-1.5">
            <Skeleton className="h-4 w-24 rounded-full" />
            <Skeleton className="h-3.5 w-14 rounded-full" />
          </Td>
          <Td>
            <Skeleton className="h-4 w-5 rounded-full" />
          </Td>
          <Td>
            <Skeleton className="h-4 w-5 rounded-full" />
          </Td>
          <Td>
            <Skeleton className="h-4 w-5 rounded-full" />
          </Td>
          <Td>
            <Skeleton className="h-4 w-8 rounded-full" />
          </Td>
          <Td divider className="hidden items-center gap-3 sm:flex">
            <Skeleton className="h-1.5 flex-1 rounded-full" />
            <Skeleton className="h-3.5 w-10 rounded-full" />
          </Td>
        </TableRow>
      ))}
    </Card>
  );
}

export function SubjectTable({
  subjects,
  total,
  className,
}: {
  subjects: Subject[];
  total: { correct: number; wrong: number; blank: number; net: string };
  className?: string;
}) {
  return (
    <Card className={`overflow-hidden ${className ?? ""}`}>
      <div className="px-5 pt-5 pb-4">
        <CardTitle>Ders sonuçları</CardTitle>
      </div>
      <Head />
      {subjects.map((subject, i) => {
        const questions = subject.correct + subject.wrong + subject.blank;
        const percent = questions > 0 ? (Number(subject.net) / questions) * 100 : 0;
        return (
          <TableRow key={subject.subjectId} cols={COLS} className="anim-fade">
            <Td>
              <div className="text-sm font-medium">{subject.subjectName}</div>
              <div className="text-[13px] text-dim">{formatInt(questions)} soru</div>
            </Td>
            <Td className="text-sm">{subject.correct}</Td>
            <Td className="text-sm">{subject.wrong}</Td>
            <Td className="text-sm">{subject.blank}</Td>
            <Td className="text-[15px] font-medium">{formatNet(subject.net)}</Td>
            <Td divider className="hidden items-center gap-3 sm:flex">
              <Bar percent={percent} delay={Math.min(i, 12) * 50} />
              <span className="w-10 text-right text-[13px] text-dim">{formatPercent(percent)}</span>
            </Td>
          </TableRow>
        );
      })}
      <TableRow cols={COLS} className="bg-surface text-sm font-medium">
        <Td>Toplam</Td>
        <Td>{total.correct}</Td>
        <Td>{total.wrong}</Td>
        <Td>{total.blank}</Td>
        <Td className="text-[15px]">{formatNet(total.net)}</Td>
        <Td className="hidden sm:block" />
      </TableRow>
    </Card>
  );
}

export function Bar({
  percent,
  accent = false,
  delay,
}: {
  percent: number;
  accent?: boolean;
  delay?: number; // ms; when set, the fill grows in from the left
}) {
  const clamped = Math.min(100, Math.max(0, percent));
  return (
    <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-[#ecEEF1]">
      <i
        className={`block h-full rounded-full ${accent ? "bg-brand" : "bg-ink"} ${delay === undefined ? "" : "anim-grow-x"}`}
        style={{
          width: `${clamped}%`,
          animationDelay: delay === undefined ? undefined : `${delay}ms`,
        }}
      />
    </div>
  );
}
