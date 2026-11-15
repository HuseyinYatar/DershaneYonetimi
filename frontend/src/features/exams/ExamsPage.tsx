import { FileText, Plus } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router";
import { useExamTypes, useMockExams } from "@/api/queries/exam";
import { useMe } from "@/api/queries/user";
import { useActor } from "@/auth/useActor";
import { StatusBadge } from "@/components/StatusBadge";
import { Card, CardHeader } from "@/components/ui/Card";
import { EmptyState, QueryError } from "@/components/ui/EmptyState";
import { PageHeader } from "@/components/ui/PageHeader";
import { PillButton } from "@/components/ui/Pill";
import { SkeletonRows } from "@/components/ui/Skeleton";
import { TableHead, TableRow, Td, Th } from "@/components/ui/Table";
import { formatFullDate, parseDateOnly } from "@/lib/format";
import { scoreSourceLabel } from "@/lib/score";
import { examBadge } from "@/lib/status/exams";
import { NewExamForm } from "./NewExamForm";
import { WeakTopicSettingCard } from "./WeakTopicSettingCard";

const COLS =
  "grid-cols-[minmax(0,1fr)_auto] md:grid-cols-[minmax(0,1.4fr)_100px_130px_170px_160px]";

export function ExamsPage() {
  const actor = useActor();
  const me = useMe();
  const exams = useMockExams(actor?.branchId ?? me.data?.branchId);
  const examTypes = useExamTypes();
  const typeName = (id: string) => examTypes.data?.find((t) => t.id === id)?.name ?? "Deneme";
  const list = [...(exams.data ?? [])].sort((a, b) => b.examDate.localeCompare(a.examDate));
  // Creating exams and the weak-topic setting are admin-only (the services enforce it too).
  const isAdmin = !!actor?.roles.includes("admin");
  const [creating, setCreating] = useState(false);

  return (
    <>
      <PageHeader eyebrow="Şubenin denemeleri" title="Denemeler" />
      {isAdmin && (
        <div className="mt-4 flex flex-col gap-4">
          <div>
            <PillButton onClick={() => setCreating(!creating)}>
              <Plus size={14} strokeWidth={1.75} aria-hidden="true" />
              Yeni deneme
            </PillButton>
          </div>
          {creating && <NewExamForm onDone={() => setCreating(false)} />}
          <WeakTopicSettingCard />
        </div>
      )}
      <Card className="mt-6 overflow-hidden">
        <CardHeader
          className="px-5 pt-5 pb-4"
          title={
            <>
              Tüm denemeler <span className="font-normal text-dim">{list.length}</span>
            </>
          }
        />
        <TableHead cols={COLS}>
          <Th>Deneme</Th>
          <Th className="hidden md:flex">Tür</Th>
          <Th className="hidden md:flex">Tarih</Th>
          <Th className="hidden md:flex">Puanlama</Th>
          <Th>Durum</Th>
        </TableHead>
        {exams.isError ? (
          <QueryError error={exams.error} />
        ) : exams.isPending ? (
          <div className="pt-4">
            <SkeletonRows rows={4} />
          </div>
        ) : list.length === 0 ? (
          <EmptyState
            icon={FileText}
            title="Deneme yok"
            text="Şubede henüz deneme tanımlanmamış."
          />
        ) : (
          list.map((exam) => {
            const badge = examBadge(exam.status);
            const date = formatFullDate(parseDateOnly(exam.examDate));
            return (
              <TableRow key={exam.id} cols={COLS} className="relative hover:bg-surface">
                <Td>
                  <Link
                    to={`/exams/${exam.id}`}
                    className="block text-sm font-medium after:absolute after:inset-0 hover:underline"
                  >
                    {exam.name}
                  </Link>
                  <div className="text-[13px] text-dim">
                    {exam.publisher ? `${exam.publisher} · ` : ""}
                    <span className="md:hidden">
                      {typeName(exam.examTypeId)} · {date}
                    </span>
                  </div>
                </Td>
                <Td className="hidden text-sm md:block">{typeName(exam.examTypeId)}</Td>
                <Td className="hidden text-sm md:block">{date}</Td>
                <Td className="hidden text-sm md:block">{scoreSourceLabel(exam.scoringSource)}</Td>
                <Td>
                  <span className="md:hidden">
                    <StatusBadge tone={badge.tone} label={badge.label} iconOnly />
                  </span>
                  <span className="hidden md:inline-flex">
                    <StatusBadge tone={badge.tone} label={badge.label} />
                  </span>
                </Td>
              </TableRow>
            );
          })
        )}
      </Card>
    </>
  );
}
