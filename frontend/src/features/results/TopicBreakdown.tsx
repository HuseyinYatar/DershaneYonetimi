import { ChartNoAxesColumn, Hash } from "lucide-react";
import { useState } from "react";
import type { components } from "@/api/schema/exam-analysis";
import { Card, CardTitle } from "@/components/ui/Card";
import { EmptyState } from "@/components/ui/EmptyState";
import { SegButton } from "@/components/ui/Pill";
import { Skeleton } from "@/components/ui/Skeleton";
import { TableHead, TableRow, Td, Th } from "@/components/ui/Table";
import { formatNet, formatPercent, ratioToPercent } from "@/lib/format";
import { Bar } from "./SubjectTable";

type Subject = components["schemas"]["SubjectResultRead"];
type Topic = components["schemas"]["TopicResultRead"];

const COLS =
  "grid-cols-[minmax(0,1fr)_44px_44px_64px] sm:grid-cols-[minmax(0,1fr)_64px_64px_190px]";

function Head() {
  return (
    <TableHead cols={COLS}>
      <Th icon={Hash}>Konu</Th>
      <Th>Soru</Th>
      <Th>D</Th>
      <Th icon={ChartNoAxesColumn} divider className="sm:pl-4">
        <span className="hidden sm:inline">Başarı</span>
        <span className="sm:hidden">%</span>
      </Th>
    </TableHead>
  );
}

function RowsSkeleton() {
  return (
    <>
      {[0, 1, 2, 3].map((i) => (
        <TableRow key={i} cols={COLS}>
          <Td>
            <Skeleton className="h-4 w-3/4 rounded-full" />
          </Td>
          <Td>
            <Skeleton className="h-4 w-5 rounded-full" />
          </Td>
          <Td>
            <Skeleton className="h-4 w-5 rounded-full" />
          </Td>
          <Td divider className="flex items-center gap-3">
            <Skeleton className="hidden h-1.5 flex-1 rounded-full sm:block" />
            <Skeleton className="h-3.5 w-10 rounded-full" />
          </Td>
        </TableRow>
      ))}
    </>
  );
}

// Shown before the exam (and so its subjects) is known: title, subject pills and topic rows.
export function TopicBreakdownSkeleton({ className }: { className?: string }) {
  return (
    <Card className={`overflow-hidden ${className ?? ""}`}>
      <div className="flex flex-wrap items-center justify-between gap-3 px-5 pt-5 pb-4">
        <CardTitle>Konu dağılımı</CardTitle>
        <div className="flex gap-1.5">
          {[0, 1, 2].map((i) => (
            <Skeleton key={i} className="h-8 w-20 rounded-full" />
          ))}
        </div>
      </div>
      <Head />
      <RowsSkeleton />
    </Card>
  );
}

export function TopicBreakdown({
  subjects,
  topics,
  topicIdsBySubject,
  loading,
  className,
}: {
  subjects: Subject[];
  topics: Topic[];
  topicIdsBySubject: Map<string, Set<string>>;
  loading: boolean;
  className?: string;
}) {
  const [picked, setPicked] = useState<string>();
  const subject = subjects.find((s) => s.subjectId === picked) ?? subjects[0];
  if (!subject) return null;

  const rows = topics.filter((t) => topicIdsBySubject.get(subject.subjectId)?.has(t.topicId));
  const totalQuestions = rows.reduce((sum, t) => sum + t.questionCount, 0);
  const totalCorrect = rows.reduce((sum, t) => sum + t.correct, 0);
  const weakest = rows.length > 0 ? Math.min(...rows.map((t) => Number(t.successRate))) : null;

  return (
    <Card className={`overflow-hidden ${className ?? ""}`}>
      <div className="flex flex-wrap items-center justify-between gap-3 px-5 pt-5 pb-4">
        <CardTitle>Konu dağılımı</CardTitle>
        <div role="group" aria-label="Ders seç" className="flex flex-wrap gap-1.5">
          {subjects.map((s) => (
            <SegButton
              key={s.subjectId}
              active={s.subjectId === subject.subjectId}
              onClick={() => setPicked(s.subjectId)}
            >
              {s.subjectName}
            </SegButton>
          ))}
        </div>
      </div>
      <Head />
      {loading ? (
        <div role="status" aria-label="Yükleniyor">
          <RowsSkeleton />
        </div>
      ) : rows.length === 0 ? (
        <EmptyState text="Bu ders için konu verisi yok." />
      ) : (
        rows.map((topic, i) => {
          const percent = ratioToPercent(topic.successRate);
          return (
            <TableRow key={topic.topicId} cols={COLS}>
              <Td>
                <div className="text-sm font-medium">{topic.topicName}</div>
              </Td>
              <Td className="text-sm">{topic.questionCount}</Td>
              <Td className="text-sm">{topic.correct}</Td>
              <Td divider className="flex items-center gap-3">
                <span className="hidden flex-1 sm:flex">
                  <Bar
                    percent={percent}
                    accent={Number(topic.successRate) === weakest}
                    delay={Math.min(i, 12) * 50}
                  />
                </span>
                <span className="w-10 text-right text-[13px] text-dim">
                  {formatPercent(percent)}
                </span>
              </Td>
            </TableRow>
          );
        })
      )}
      <TableRow cols={COLS} className="bg-surface text-sm font-medium">
        <Td>
          {subject.subjectName} toplam{" "}
          <span className="font-normal text-dim">· net {formatNet(subject.net)}</span>
        </Td>
        <Td>{totalQuestions}</Td>
        <Td>{totalCorrect}</Td>
        <Td />
      </TableRow>
    </Card>
  );
}
