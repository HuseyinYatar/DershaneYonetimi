import { Check, Minus, Target, X } from "lucide-react";
import { useParams } from "react-router";
import {
  useExamSubjectNames,
  useStudentResult,
  useSubjectTopicIds,
  useWeakTopics,
} from "@/api/queries/exam";
import { isUnavailable, QueryError, UnavailableState } from "@/components/ui/EmptyState";
import { Breadcrumb, PageHeader } from "@/components/ui/PageHeader";
import { Skeleton } from "@/components/ui/Skeleton";
import { StatStrip, StatStripSkeleton } from "@/components/ui/StatStrip";
import { formatFullDateWithWeekday, formatNet, parseDateOnly } from "@/lib/format";
import { ScoreCard, ScoreCardSkeleton } from "./ScoreCard";
import { StudyList } from "./StudyList";
import { SubjectTable, SubjectTableSkeleton } from "./SubjectTable";
import { TopicBreakdown, TopicBreakdownSkeleton } from "./TopicBreakdown";

// Notification e-mails link straight here, so everything loads from the URL ids (cold load works).
export function ResultDetailPage() {
  const { studentId = "", mockExamId } = useParams();
  const result = useStudentResult(studentId, mockExamId);
  const weak = useWeakTopics(studentId);
  const subjectNames = useExamSubjectNames();
  const subjects = result.data?.subjects ?? [];
  const topicIds = useSubjectTopicIds(subjects.map((s) => s.subjectId));

  const backLink = { label: "Denemeler", to: `/students/${studentId}/results` };

  if (result.isError) {
    return (
      <>
        <PageHeader
          breadcrumb={<Breadcrumb parent={backLink} current="Deneme sonucu" />}
          title="Deneme sonucu"
        />
        {isUnavailable(result.error) ? (
          <UnavailableState className="py-16" />
        ) : (
          <QueryError error={result.error} />
        )}
      </>
    );
  }
  if (!result.data) {
    return (
      <>
        <PageHeader
          breadcrumb={<Breadcrumb parent={backLink} current="…" />}
          eyebrow={<Skeleton className="h-4 w-56 max-w-full rounded-full" />}
          title={<Skeleton className="mt-2 h-9 w-72 max-w-full rounded-full md:h-10" />}
        />
        <div role="status" aria-label="Yükleniyor">
          <div className="mt-6">
            <StatStripSkeleton />
          </div>
          <div className="mt-5 grid gap-4 lg:grid-cols-2 wide:grid-cols-3">
            <ScoreCardSkeleton />
            <SubjectTableSkeleton className="lg:col-span-2" />
          </div>
          <div className="mt-4 grid items-start gap-4 lg:grid-cols-2 wide:grid-cols-3">
            <TopicBreakdownSkeleton className="lg:col-span-2" />
            <StudyList topics={[]} windowSize={undefined} subjectNames={new Map()} loading />
          </div>
        </div>
      </>
    );
  }

  const data = result.data;
  const questionCount = data.correct + data.wrong + data.blank;
  const examSubjectIds = new Set(subjects.map((s) => s.subjectId));
  const studyTopics = (weak.data?.weakTopics ?? []).filter((t) => examSubjectIds.has(t.subjectId));

  return (
    <>
      <PageHeader
        breadcrumb={<Breadcrumb parent={backLink} current={data.examName} />}
        eyebrow={`${formatFullDateWithWeekday(parseDateOnly(data.examDate))} · ${questionCount} soru`}
        title={data.examName}
      />
      <div className="mt-6">
        <StatStrip
          stats={[
            { icon: Check, value: String(data.correct), label: "doğru" },
            { icon: X, value: String(data.wrong), label: "yanlış" },
            { icon: Minus, value: String(data.blank), label: "boş" },
            { icon: Target, value: formatNet(data.net), label: "net" },
          ]}
        />
      </div>

      <div className="mt-5 grid gap-4 lg:grid-cols-2 wide:grid-cols-3">
        {data.scores.length > 0 && <ScoreCard scores={data.scores} />}
        <SubjectTable
          subjects={subjects}
          total={{ correct: data.correct, wrong: data.wrong, blank: data.blank, net: data.net }}
          className={data.scores.length > 0 ? "lg:col-span-2" : "lg:col-span-2 wide:col-span-3"}
        />
      </div>

      <div className="mt-4 grid items-start gap-4 lg:grid-cols-2 wide:grid-cols-3">
        <TopicBreakdown
          subjects={subjects}
          topics={data.topics}
          topicIdsBySubject={topicIds.bySubject}
          loading={topicIds.isPending}
          className="lg:col-span-2"
        />
        <StudyList
          topics={studyTopics}
          windowSize={weak.data?.windowSize}
          subjectNames={subjectNames}
          loading={weak.isPending}
        />
      </div>
    </>
  );
}
