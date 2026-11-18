import { zodResolver } from "@hookform/resolvers/zod";
import { GraduationCap, Users } from "lucide-react";
import { useForm } from "react-hook-form";
import { Link, useParams } from "react-router";
import {
  useClassroom,
  useClassroomStudents,
  useClassroomTeachers,
  useDeactivateClassroom,
  useRemoveFromClassroom,
  useUpdateClassroom,
} from "@/api/queries/user";
import { StatusBadge } from "@/components/StatusBadge";
import { Card, CardHeader } from "@/components/ui/Card";
import { EmptyState, QueryError } from "@/components/ui/EmptyState";
import { PRIMARY_BUTTON } from "@/components/ui/form";
import { Breadcrumb, PageHeader } from "@/components/ui/PageHeader";
import { PillButton } from "@/components/ui/Pill";
import { useConfirm } from "@/lib/useConfirm";
import { Skeleton, SkeletonRows } from "@/components/ui/Skeleton";
import { classroomFormSchema, type ClassroomFormValues } from "@/lib/classroomForm";
import { activeBadge } from "@/lib/status/admin";
import { userPath } from "@/lib/userForm";
import { ClassroomFields } from "./ClassroomFields";

const BACK = { label: "Sınıflar", to: "/classrooms" };

type Classroom = NonNullable<ReturnType<typeof useClassroom>["data"]>;

function EditCard({ classroom }: { classroom: Classroom }) {
  const update = useUpdateClassroom(classroom.id ?? "");
  const ask = useConfirm();
  const deactivate = useDeactivateClassroom(classroom.id ?? "");
  const {
    register,
    control,
    handleSubmit,
    formState: { errors, isDirty },
  } = useForm<ClassroomFormValues>({
    resolver: zodResolver(classroomFormSchema),
    defaultValues: {
      name: classroom.name ?? "",
      gradeLevel: String(classroom.gradeLevel ?? ""),
      term: classroom.term ?? "",
    },
  });
  const active = classroom.active !== false;

  return (
    <Card className="p-5">
      <CardHeader
        title="Bilgiler"
        action={
          active && (
            <PillButton
              disabled={deactivate.isPending}
              onClick={async () => {
                if (
                  await ask({
                    title: "Sınıf pasife alınsın mı?",
                    text: "Öğrencisi olan sınıf pasife alınamaz.",
                    confirmLabel: "Pasife al",
                    danger: true,
                  })
                ) {
                  deactivate.mutate();
                }
              }}
            >
              Pasife al
            </PillButton>
          )
        }
      />
      <form
        onSubmit={handleSubmit((v) => update.mutate({ name: v.name.trim(), term: v.term.trim() }))}
        noValidate
        className="mt-4 flex flex-col gap-4"
      >
        <ClassroomFields register={register} control={control} errors={errors} withGrade={false} />
        <p className="text-[13px] text-dim">
          Seviye: {classroom.gradeLevel}. sınıf (sonradan değişmez).
        </p>
        <div>
          <button
            type="submit"
            disabled={!isDirty || update.isPending || !active}
            className={PRIMARY_BUTTON}
          >
            {update.isPending ? "Kaydediliyor…" : "Kaydet"}
          </button>
        </div>
      </form>
    </Card>
  );
}

function StudentsCard({ classroomId }: { classroomId: string }) {
  const students = useClassroomStudents(classroomId);
  const ask = useConfirm();
  const remove = useRemoveFromClassroom();
  const list = students.data ?? [];

  return (
    <Card className="p-5">
      <CardHeader
        title={
          <>
            Öğrenciler <span className="font-normal text-dim">{list.length}</span>
          </>
        }
      />
      {students.isError ? (
        <QueryError error={students.error} />
      ) : students.isPending ? (
        <div className="-mx-5 mt-4">
          <SkeletonRows rows={3} />
        </div>
      ) : list.length === 0 ? (
        <EmptyState
          icon={Users}
          title="Öğrenci yok"
          text="Öğrenciler, öğrencinin sayfasından sınıfa eklenir."
        />
      ) : (
        <ul className="mt-2">
          {list.map((s) => (
            <li
              key={s.studentId}
              className="anim-fade flex items-center justify-between gap-3 border-t border-line py-3 first:border-t-0"
            >
              <div>
                <Link
                  to={userPath("student", s.studentId ?? "")}
                  className="text-sm font-medium hover:underline"
                >
                  {s.firstName} {s.lastName}
                </Link>
                <div className="text-[13px] text-dim">No {s.studentNumber}</div>
              </div>
              <PillButton
                disabled={remove.isPending}
                onClick={async () => {
                  if (
                    await ask({
                      title: "Öğrenci sınıftan çıkarılsın mı?",
                      confirmLabel: "Çıkar",
                      danger: true,
                    })
                  ) {
                    remove.mutate(s.studentId ?? "");
                  }
                }}
              >
                Çıkar
              </PillButton>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

function TeachersCard({ classroomId }: { classroomId: string }) {
  const teachers = useClassroomTeachers(classroomId);
  const list = teachers.data ?? [];

  return (
    <Card className="p-5">
      <CardHeader
        title={
          <>
            Öğretmenler <span className="font-normal text-dim">{list.length}</span>
          </>
        }
      />
      {teachers.isError ? (
        <QueryError error={teachers.error} />
      ) : teachers.isPending ? (
        <div className="-mx-5 mt-4">
          <SkeletonRows rows={2} />
        </div>
      ) : list.length === 0 ? (
        <EmptyState
          icon={GraduationCap}
          title="Öğretmen yok"
          text="Atamalar öğretmenin sayfasından yapılır."
        />
      ) : (
        <ul className="mt-2">
          {list.map((t) => (
            <li
              key={t.assignmentId}
              className="anim-fade border-t border-line py-3 first:border-t-0"
            >
              <Link
                to={userPath("teacher", t.teacherId ?? "")}
                className="text-sm font-medium hover:underline"
              >
                {t.firstName} {t.lastName}
              </Link>
              <div className="text-[13px] text-dim">{t.subjectName}</div>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

export function AdminClassroomDetailPage() {
  const { classroomId = "" } = useParams();
  const classroom = useClassroom(classroomId);
  const data = classroom.data;

  if (classroom.isError) {
    return (
      <>
        <PageHeader breadcrumb={<Breadcrumb parent={BACK} current="Sınıf" />} title="Sınıf" />
        <QueryError error={classroom.error} className="py-16" />
      </>
    );
  }
  if (!data) {
    return (
      <>
        <PageHeader breadcrumb={<Breadcrumb parent={BACK} current="…" />} title="Sınıf" />
        <div role="status" aria-label="Yükleniyor" className="mt-6 flex flex-col gap-4">
          <Skeleton className="h-8 w-28" />
          <Skeleton className="h-48" />
          <Skeleton className="h-48" />
        </div>
      </>
    );
  }
  const badge = activeBadge(data.active);

  return (
    <>
      <PageHeader
        breadcrumb={<Breadcrumb parent={BACK} current={data.name ?? "Sınıf"} />}
        eyebrow={`${data.gradeLevel}. sınıf · ${data.term}`}
        title={data.name ?? "Sınıf"}
      />
      <div className="mt-4">
        <StatusBadge tone={badge.tone} label={badge.label} />
      </div>
      <div className="mt-5 grid max-w-3xl gap-4">
        {/* Keyed by updatedAt so a refetch resets the form to the stored values. */}
        <EditCard key={data.updatedAt} classroom={data} />
        <StudentsCard classroomId={classroomId} />
        <TeachersCard classroomId={classroomId} />
      </div>
    </>
  );
}
