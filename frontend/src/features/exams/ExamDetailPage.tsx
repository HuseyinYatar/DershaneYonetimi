import { ChartNoAxesColumn } from "lucide-react";
import { Link, useParams } from "react-router";
import {
  useExamResults,
  useExamSubjectNames,
  useMockExam,
  useTopicAnalysis,
} from "@/api/queries/exam";
import { ScoreInline } from "@/components/ScoreInline";
import { StatusBadge } from "@/components/StatusBadge";
import { Bar } from "@/components/ui/Bar";
import { Card, CardHeader } from "@/components/ui/Card";
import { EmptyState, QueryError } from "@/components/ui/EmptyState";
import { Breadcrumb, PageHeader } from "@/components/ui/PageHeader";
import { SkeletonRows } from "@/components/ui/Skeleton";
import { TableHead, TableRow, Td, Th } from "@/components/ui/Table";
import {
  formatFullDateWithWeekday,
  formatInt,
  formatNet,
  formatPercent,
  parseDateOnly,
  ratioToPercent,
} from "@/lib/format";
import { examBadge } from "@/lib/status/exams";

const BACK = { label: "Denemeler", to: "/exams" };
const RESULT_COLS =
  "grid-cols-[minmax(0,1fr)_64px] md:grid-cols-[minmax(0,1.2fr)_50px_50px_50px_70px_minmax(0,1.4fr)_60px]";
const TOPIC_COLS = "grid-cols-[minmax(0,1fr)_64px] md:grid-cols-[minmax(0,1fr)_90px_190px]";

function ResultsCard({ mockExamId }: { mockExamId: string }) {
  const results = useExamResults(mockExamId);
  const list = results.data ?? [];

  return (
    <Card className="overflow-hidden">
      <CardHeader
        className="px-5 pt-5 pb-4"
        title={
          <>
            Sonuçlar <span className="font-normal text-dim">{list.length}</span>
          </>
        }
      />
      <TableHead cols={RESULT_COLS}>
        <Th>Öğrenci</Th>
        <Th className="hidden md:flex">D</Th>
        <Th className="hidden md:flex">Y</Th>
        <Th className="hidden md:flex">B</Th>
        <Th>Net</Th>
        <Th className="hidden md:flex">Puan</Th>
        <Th className="hidden md:flex">Sıra</Th>
      </TableHead>
      {results.isError ? (
        <QueryError error={results.error} />
      ) : results.isPending ? (
        <div className="pt-4">
          <SkeletonRows rows={5} />
        </div>
      ) : list.length === 0 ? (
        <EmptyState text="Bu deneme için henüz sonuç yok." />
      ) : (
        list.map((row) => (
          <TableRow key={row.studentId} cols={RESULT_COLS} className="anim-fade">
            <Td>
              <Link
                to={`/students/${row.studentId}/results/${mockExamId}`}
                className="text-sm font-medium hover:underline"
              >
                {row.fullName ?? "Öğrenci"}
              </Link>
              <div className="text-[13px] text-dim md:hidden">
                {row.branchRank === null ? "—" : `${formatInt(row.branchRank)}. sıra`}
              </div>
            </Td>
            <Td className="hidden text-sm md:block">{row.correct}</Td>
            <Td className="hidden text-sm md:block">{row.wrong}</Td>
            <Td className="hidden text-sm md:block">{row.blank}</Td>
            <Td className="text-[15px] font-medium">{formatNet(row.net)}</Td>
            <Td className="hidden flex-col gap-1 md:flex">
              {row.scores.length === 0 ? (
                <span className="text-dim">—</span>
              ) : (
                row.scores.map((score) => <ScoreInline key={score.scoreTypeId} score={score} />)
              )}
            </Td>
            <Td className="hidden text-sm md:block">
              {row.branchRank === null ? "—" : formatInt(row.branchRank)}
            </Td>
          </TableRow>
        ))
      )}
    </Card>
  );
}

// Weakest topic first (the service sorts by success rate); its bar is the accent one.
function TopicsCard({ mockExamId }: { mockExamId: string }) {
  const analysis = useTopicAnalysis(mockExamId);
  const subjectNames = useExamSubjectNames();
  const list = analysis.data ?? [];

  return (
    <Card className="overflow-hidden">
      <CardHeader className="px-5 pt-5 pb-4" title="Konu analizi" />
      <TableHead cols={TOPIC_COLS}>
        <Th>Konu</Th>
        <Th className="hidden md:flex">Soru</Th>
        <Th icon={ChartNoAxesColumn} divider>
          Başarı
        </Th>
      </TableHead>
      {analysis.isError ? (
        <QueryError error={analysis.error} />
      ) : analysis.isPending ? (
        <div className="pt-4">
          <SkeletonRows rows={5} />
        </div>
      ) : list.length === 0 ? (
        <EmptyState text="Bu deneme için konu verisi yok." />
      ) : (
        list.map((topic, i) => {
          const percent = ratioToPercent(topic.successRate);
          return (
            <TableRow key={topic.topicId} cols={TOPIC_COLS}>
              <Td>
                <div className="text-sm font-medium">{topic.topicName}</div>
                <div className="text-[13px] text-dim">
                  {subjectNames.get(topic.subjectId) ?? "Ders"} · {topic.studentCount} öğrenci
                </div>
              </Td>
              <Td className="hidden text-sm md:block">
                {topic.correct} / {topic.questionCount}
              </Td>
              <Td divider className="flex items-center gap-3">
                <span className="hidden flex-1 md:flex">
                  <Bar percent={percent} accent={i === 0} delay={Math.min(i, 12) * 50} />
                </span>
                <span className="w-10 text-right text-[13px] text-dim">
                  {formatPercent(percent)}
                </span>
              </Td>
            </TableRow>
          );
        })
      )}
    </Card>
  );
}

export function ExamDetailPage() {
  const { mockExamId = "" } = useParams();
  const exam = useMockExam(mockExamId);
  const data = exam.data;
  const badge = data ? examBadge(data.status) : undefined;

  return (
    <>
      <PageHeader
        breadcrumb={<Breadcrumb parent={BACK} current={data?.name ?? "Deneme"} />}
        eyebrow={data ? formatFullDateWithWeekday(parseDateOnly(data.examDate)) : undefined}
        title={data?.name ?? "Deneme"}
      />
      {badge && (
        <div className="mt-4">
          <StatusBadge tone={badge.tone} label={badge.label} />
        </div>
      )}
      {exam.isError ? (
        <QueryError error={exam.error} className="py-16" />
      ) : (
        <div className="mt-5 flex flex-col gap-4">
          <ResultsCard mockExamId={mockExamId} />
          <TopicsCard mockExamId={mockExamId} />
        </div>
      )}
    </>
  );
}
