import { useParams } from "react-router";
import { useGuardian, useStudent, useTeacher, type UserRow } from "@/api/queries/user";
import { StatusBadge } from "@/components/StatusBadge";
import { QueryError } from "@/components/ui/EmptyState";
import { Breadcrumb, PageHeader } from "@/components/ui/PageHeader";
import { Skeleton } from "@/components/ui/Skeleton";
import { activeBadge } from "@/lib/status/admin";
import { KIND_LABELS, fullName, type UserKind } from "@/lib/userForm";
import { AccountCard } from "./AccountCard";
import { GuardianChildrenCard } from "./GuardianChildrenCard";
import { EnrollmentCard, GuardianCard } from "./StudentCards";
import { TeacherAssignmentsCard } from "./TeacherAssignmentsCard";
import { UserEditCard } from "./UserEditCard";

const BACK = { label: "Kullanıcılar", to: "/users" };

function UserDetailSkeleton() {
  return (
    <div role="status" aria-label="Yükleniyor" className="mt-6 flex flex-col gap-4">
      <Skeleton className="h-8 w-28" />
      <Skeleton className="h-72" />
      <Skeleton className="h-32" />
    </div>
  );
}

export function UserDetailPage({ kind }: { kind: UserKind }) {
  const { userId = "" } = useParams();
  const student = useStudent(userId, kind === "student");
  const teacher = useTeacher(userId, kind === "teacher");
  const guardian = useGuardian(userId, kind === "guardian");
  const query = kind === "student" ? student : kind === "teacher" ? teacher : guardian;
  const user: UserRow | undefined = query.data;
  const label = KIND_LABELS[kind].singular;

  if (query.isError) {
    return (
      <>
        <PageHeader breadcrumb={<Breadcrumb parent={BACK} current={label} />} title={label} />
        <QueryError error={query.error} className="py-16" />
      </>
    );
  }
  if (!user) {
    return (
      <>
        <PageHeader breadcrumb={<Breadcrumb parent={BACK} current="…" />} title={label} />
        <UserDetailSkeleton />
      </>
    );
  }

  const badge = activeBadge(user.active);
  const active = user.active !== false;

  return (
    <>
      <PageHeader
        breadcrumb={<Breadcrumb parent={BACK} current={fullName(user)} />}
        eyebrow={label}
        title={fullName(user)}
      />
      <div className="mt-4">
        <StatusBadge tone={badge.tone} label={badge.label} />
      </div>
      <div className="mt-5 grid max-w-3xl gap-4">
        {/* Keyed by updatedAt: a refetch after a save or a concurrent edit resets the form. */}
        <UserEditCard key={user.updatedAt} kind={kind} user={user} />
        {"gradeLevel" in user && (
          <>
            <EnrollmentCard studentId={userId} gradeLevel={user.gradeLevel ?? 0} active={active} />
            <GuardianCard studentId={userId} active={active} />
          </>
        )}
        {kind === "teacher" && <TeacherAssignmentsCard teacherId={userId} active={active} />}
        {kind === "guardian" && <GuardianChildrenCard guardianId={userId} />}
        <AccountCard user={user} />
      </div>
    </>
  );
}
