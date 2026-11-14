import { useState } from "react";
import { Link, useParams } from "react-router";
import { useExamTypes, useProgress, useStudentResults } from "@/api/queries/exam";
import { NetProgress } from "@/components/NetProgress";
import { Card, CardHeader } from "@/components/ui/Card";
import { EmptyState, QueryError } from "@/components/ui/EmptyState";
import { PageHeader } from "@/components/ui/PageHeader";
import { SegButton } from "@/components/ui/Pill";
import { LineSkeleton } from "@/components/ui/ChartSkeletons";
import { SkeletonRows } from "@/components/ui/Skeleton";
import { TableHead, TableRow, Td, Th } from "@/components/ui/Table";
import { formatFullDate, formatInt, formatNet, parseDateOnly } from "@/lib/format";
import { ScoreInline } from "./ScoreChip";

const COLS =
  "grid-cols-[minmax(0,1fr)_72px] md:grid-cols-[minmax(0,1.3fr)_130px_80px_minmax(0,1.4fr)_70px]";

export function ResultsPage() {
  const { studentId = "" } = useParams();
  const results = useStudentResults(studentId);
  const examTypes = useExamTypes();
  const [picked, setPicked] = useState<string>();

  const list = results.data ?? [];
  // Exam types the student has results in, newest first; the switcher only shows for more than one.
  const typeIds = [...new Set(list.map((r) => r.examTypeId))];
  const typeId = picked && typeIds.includes(picked) ? picked : typeIds[0];
  const typeName = (id: string) => examTypes.data?.find((t) => t.id === id)?.name ?? "Deneme";
  const progress = useProgress(studentId, typeId);
  const points = progress.data?.points ?? [];

  return (
    <>
      <PageHeader eyebrow="Açıklanmış denemelerin" title="Denemeler" />
      <div className="mt-5 flex flex-col gap-4">
        <Card className="p-5">
          <CardHeader
            title="Net gelişimi"
            action={
              typeIds.length > 1 && (
                <div role="group" aria-label="Sınav türü" className="flex flex-wrap gap-1.5">
                  {typeIds.map((id) => (
                    <SegButton key={id} active={id === typeId} onClick={() => setPicked(id)}>
                      {typeName(id)}
                    </SegButton>
                  ))}
                </div>
              )
            }
          />
          <div className="mt-4">
            {results.isError ? (
              <QueryError error={results.error} />
            ) : progress.isError ? (
              <QueryError error={progress.error} />
            ) : results.isPending || (typeId && progress.isPending) ? (
              <LineSkeleton />
            ) : points.length === 0 ? (
              <EmptyState text="Henüz açıklanmış deneme yok." />
            ) : (
              <NetProgress points={points} />
            )}
          </div>
        </Card>

        <Card className="overflow-hidden">
          <CardHeader title="Tüm denemeler" className="px-5 pt-5 pb-4" />
          <TableHead cols={COLS}>
            <Th>Deneme</Th>
            <Th className="hidden md:flex">Tarih</Th>
            <Th>Net</Th>
            <Th className="hidden md:flex">Puan</Th>
            <Th className="hidden md:flex">Sıra</Th>
          </TableHead>
          {results.isError ? (
            <QueryError error={results.error} />
          ) : results.isPending ? (
            <div className="pt-4">
              <SkeletonRows rows={3} />
            </div>
          ) : list.length === 0 ? (
            <EmptyState text="Henüz açıklanmış deneme yok." />
          ) : (
            list.map((result) => (
              <TableRow key={result.mockExamId} cols={COLS} className="gap-y-1">
                <Td>
                  <Link
                    to={`/students/${studentId}/results/${result.mockExamId}`}
                    className="block text-sm leading-snug font-medium hover:underline"
                  >
                    {result.examName}
                  </Link>
                  <div className="text-[13px] text-dim">
                    {typeName(result.examTypeId)} · {formatFullDate(parseDateOnly(result.examDate))}
                  </div>
                  <div className="mt-1.5 flex flex-col gap-1 md:hidden">
                    {result.scores.map((score) => (
                      <ScoreInline key={score.scoreTypeId} score={score} />
                    ))}
                  </div>
                </Td>
                <Td className="hidden text-sm md:block">
                  {formatFullDate(parseDateOnly(result.examDate))}
                </Td>
                <Td className="text-[15px] font-medium">{formatNet(result.net)}</Td>
                <Td className="hidden flex-col gap-1 md:flex">
                  {result.scores.length === 0 ? (
                    <span className="text-dim">—</span>
                  ) : (
                    result.scores.map((score) => (
                      <ScoreInline key={score.scoreTypeId} score={score} />
                    ))
                  )}
                </Td>
                <Td className="hidden text-sm md:block">
                  {result.branchRank === null ? "—" : formatInt(result.branchRank)}
                </Td>
              </TableRow>
            ))
          )}
        </Card>
      </div>
    </>
  );
}
