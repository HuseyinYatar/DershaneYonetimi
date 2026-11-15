import { zodResolver } from "@hookform/resolvers/zod";
import { Plus, School } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link } from "react-router";
import { useBranchClassrooms, useCreateClassroom } from "@/api/queries/user";
import { useBranchId } from "@/auth/useBranchId";
import { StatusBadge } from "@/components/StatusBadge";
import { Card, CardHeader } from "@/components/ui/Card";
import { EmptyState, QueryError } from "@/components/ui/EmptyState";
import { PRIMARY_BUTTON } from "@/components/ui/form";
import { PageHeader } from "@/components/ui/PageHeader";
import { PillButton, SegButton } from "@/components/ui/Pill";
import { SkeletonRows } from "@/components/ui/Skeleton";
import { TableHead, TableRow, Td, Th } from "@/components/ui/Table";
import {
  classroomFormSchema,
  toCreateClassroom,
  type ClassroomFormValues,
} from "@/lib/classroomForm";
import { formatDateLong, formatInt } from "@/lib/format";
import { activeBadge } from "@/lib/status/admin";
import { useIdempotencyKey } from "@/lib/useIdempotencyKey";
import { ClassroomFields } from "./ClassroomFields";

const COLS = "grid-cols-[minmax(0,1fr)_auto] md:grid-cols-[minmax(0,1fr)_110px_110px_90px_100px]";

function NewClassroomForm({ onDone }: { onDone: () => void }) {
  const branchId = useBranchId();
  const create = useCreateClassroom();
  const idempotency = useIdempotencyKey();
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ClassroomFormValues>({
    resolver: zodResolver(classroomFormSchema),
    defaultValues: { name: "", gradeLevel: "", term: "" },
  });

  const onSubmit = handleSubmit((values) => {
    if (!branchId) return;
    create.mutate(
      { body: toCreateClassroom(values, branchId), idempotencyKey: idempotency.key },
      {
        onSuccess: () => {
          idempotency.renew();
          onDone();
        },
        onError: idempotency.renewOnClientError,
      },
    );
  });

  return (
    <Card className="p-5">
      <CardHeader title="Yeni sınıf" />
      <form onSubmit={onSubmit} noValidate className="mt-4 flex flex-col gap-4">
        <ClassroomFields register={register} errors={errors} withGrade />
        <div className="flex gap-2">
          <button type="submit" disabled={create.isPending || !branchId} className={PRIMARY_BUTTON}>
            {create.isPending ? "Oluşturuluyor…" : "Sınıfı oluştur"}
          </button>
          <PillButton onClick={onDone} className="h-10 px-4">
            Vazgeç
          </PillButton>
        </div>
      </form>
    </Card>
  );
}

// Branch-wide classroom list for admins; teachers see only their own classrooms (ClassroomsPage).
export function AdminClassroomsPage() {
  const classrooms = useBranchClassrooms();
  const [creating, setCreating] = useState(false);
  const [showInactive, setShowInactive] = useState(false);
  const all = classrooms.data ?? [];
  const list = showInactive ? all : all.filter((c) => c.active);

  return (
    <>
      <PageHeader eyebrow={formatDateLong(new Date())} title="Sınıflar" />
      <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
        <PillButton onClick={() => setCreating(!creating)}>
          <Plus size={14} strokeWidth={1.75} aria-hidden="true" />
          Yeni sınıf
        </PillButton>
        <SegButton active={showInactive} onClick={() => setShowInactive(!showInactive)}>
          Pasif sınıfları göster
        </SegButton>
      </div>
      {creating && (
        <div className="mt-4">
          <NewClassroomForm onDone={() => setCreating(false)} />
        </div>
      )}
      <Card className="mt-4 overflow-hidden">
        <CardHeader
          className="px-5 pt-5 pb-4"
          title={
            <>
              Sınıflar <span className="font-normal text-dim">{list.length}</span>
            </>
          }
        />
        <TableHead cols={COLS}>
          <Th>Sınıf</Th>
          <Th className="hidden md:flex">Seviye</Th>
          <Th className="hidden md:flex">Dönem</Th>
          <Th className="hidden md:flex">Öğrenci</Th>
          <Th>Durum</Th>
        </TableHead>
        {classrooms.isError ? (
          <QueryError error={classrooms.error} />
        ) : classrooms.isPending ? (
          <div className="pt-4">
            <SkeletonRows rows={4} />
          </div>
        ) : list.length === 0 ? (
          <EmptyState icon={School} title="Sınıf yok" text="Şubede henüz sınıf tanımlanmamış." />
        ) : (
          list.map((classroom) => {
            const badge = activeBadge(classroom.active);
            return (
              <TableRow
                key={classroom.id}
                cols={COLS}
                className="anim-fade relative hover:bg-surface"
              >
                <Td>
                  <Link
                    to={`/classrooms/${classroom.id}`}
                    className="block text-sm font-medium after:absolute after:inset-0 hover:underline"
                  >
                    {classroom.name}
                  </Link>
                  <div className="text-[13px] text-dim md:hidden">
                    {classroom.gradeLevel}. sınıf · {classroom.term}
                  </div>
                </Td>
                <Td className="hidden text-sm md:block">{classroom.gradeLevel}. sınıf</Td>
                <Td className="hidden text-sm md:block">{classroom.term}</Td>
                <Td className="hidden text-sm md:block">
                  {formatInt(classroom.activeStudentCount)}
                </Td>
                <Td>
                  <StatusBadge tone={badge.tone} label={badge.label} size="sm" />
                </Td>
              </TableRow>
            );
          })
        )}
      </Card>
    </>
  );
}
