import { School } from "lucide-react";
import { Link } from "react-router";
import { useMyClassrooms, useMyClassroomStudentCounts } from "@/api/queries/user";
import { Card, CardHeader } from "@/components/ui/Card";
import { EmptyState, QueryError } from "@/components/ui/EmptyState";
import { PageHeader } from "@/components/ui/PageHeader";
import { SkeletonRows } from "@/components/ui/Skeleton";
import { TableHead, TableRow, Td, Th } from "@/components/ui/Table";
import { formatDateLong, formatInt } from "@/lib/format";

const COLS = "grid-cols-[minmax(0,1fr)_90px] md:grid-cols-[minmax(0,1fr)_110px_110px_90px]";

export function ClassroomsPage() {
  const classrooms = useMyClassrooms();
  const list = classrooms.data ?? [];
  const counts = useMyClassroomStudentCounts();

  return (
    <>
      <PageHeader eyebrow={formatDateLong(new Date())} title="Sınıflarım" />
      <Card className="mt-6 overflow-hidden">
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
          <Th>Öğrenci</Th>
        </TableHead>
        {classrooms.isError ? (
          <QueryError error={classrooms.error} />
        ) : classrooms.isPending ? (
          <div className="pt-4">
            <SkeletonRows rows={3} />
          </div>
        ) : list.length === 0 ? (
          <EmptyState
            icon={School}
            title="Sınıf yok"
            text="Henüz sana atanmış bir sınıf görünmüyor."
          />
        ) : (
          list.map((classroom) => (
            <TableRow key={classroom.id} cols={COLS} className="relative hover:bg-surface">
              <Td>
                <Link
                  to={`/classrooms/${classroom.id}`}
                  className="block text-sm font-medium after:absolute after:inset-0 hover:underline"
                >
                  {classroom.name}
                </Link>
                <div className="text-[13px] text-dim md:hidden">{classroom.gradeLevel}. sınıf</div>
              </Td>
              <Td className="hidden text-sm md:block">{classroom.gradeLevel}. sınıf</Td>
              <Td className="hidden text-sm md:block">{classroom.term}</Td>
              <Td className="text-sm">{formatInt(counts.get(classroom.id))}</Td>
            </TableRow>
          ))
        )}
      </Card>
    </>
  );
}
