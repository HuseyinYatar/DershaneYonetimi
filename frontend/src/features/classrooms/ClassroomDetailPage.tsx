import { Users } from "lucide-react";
import { Link, useParams } from "react-router";
import { useClassroomStudents, useMyClassrooms } from "@/api/queries/user";
import { Card, CardHeader } from "@/components/ui/Card";
import { EmptyState, QueryError } from "@/components/ui/EmptyState";
import { Breadcrumb, PageHeader } from "@/components/ui/PageHeader";
import { PillLink } from "@/components/ui/Pill";
import { SkeletonRows } from "@/components/ui/Skeleton";
import { TableHead, TableRow, Td, Th } from "@/components/ui/Table";

const BACK = { label: "Sınıflarım", to: "/classrooms" };
const COLS = "grid-cols-[minmax(0,1fr)_auto] md:grid-cols-[minmax(0,1fr)_140px_110px]";

export function ClassroomDetailPage() {
  const { classroomId = "" } = useParams();
  const classrooms = useMyClassrooms();
  const students = useClassroomStudents(classroomId);
  const classroom = classrooms.data?.find((c) => c.id === classroomId);
  const list = students.data ?? [];

  return (
    <>
      <PageHeader
        breadcrumb={<Breadcrumb parent={BACK} current={classroom?.name ?? "Sınıf"} />}
        eyebrow={classroom ? `${classroom.gradeLevel}. sınıf · ${classroom.term}` : undefined}
        title={classroom?.name ?? "Sınıf"}
      />
      <Card className="mt-6 overflow-hidden">
        <CardHeader
          className="px-5 pt-5 pb-4"
          title={
            <>
              Öğrenciler <span className="font-normal text-dim">{list.length}</span>
            </>
          }
        />
        <TableHead cols={COLS}>
          <Th>Öğrenci</Th>
          <Th className="hidden md:flex">Öğrenci no</Th>
          <Th />
        </TableHead>
        {students.isError ? (
          <QueryError error={students.error} />
        ) : students.isPending ? (
          <div className="pt-4">
            <SkeletonRows rows={5} />
          </div>
        ) : list.length === 0 ? (
          <EmptyState
            icon={Users}
            title="Öğrenci yok"
            text="Bu sınıfta aktif öğrenci görünmüyor."
          />
        ) : (
          list.map((student) => (
            <TableRow key={student.studentId} cols={COLS}>
              <Td>
                <Link
                  to={`/students/${student.studentId}`}
                  className="text-sm font-medium hover:underline"
                >
                  {student.firstName} {student.lastName}
                </Link>
                <div className="text-[13px] text-dim md:hidden">{student.studentNumber}</div>
              </Td>
              <Td className="hidden text-sm md:block">{student.studentNumber}</Td>
              <Td className="flex justify-end">
                <PillLink to={`/students/${student.studentId}`}>Özet</PillLink>
              </Td>
            </TableRow>
          ))
        )}
      </Card>
    </>
  );
}
