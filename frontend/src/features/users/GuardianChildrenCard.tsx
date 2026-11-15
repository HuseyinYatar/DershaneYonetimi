import { Users } from "lucide-react";
import { Link } from "react-router";
import { useGuardianChildren } from "@/api/queries/user";
import { Card, CardHeader } from "@/components/ui/Card";
import { EmptyState, QueryError } from "@/components/ui/EmptyState";
import { SkeletonRows } from "@/components/ui/Skeleton";
import { RELATIONSHIP_LABELS, fullName, userPath } from "@/lib/userForm";

export function GuardianChildrenCardSkeleton() {
  return (
    <Card className="p-5">
      <CardHeader title="Çocuklar" />
      <div className="-mx-5 mt-4">
        <SkeletonRows rows={2} />
      </div>
    </Card>
  );
}

// Children are linked from the student's page (a student has one guardian).
export function GuardianChildrenCard({ guardianId }: { guardianId: string }) {
  const children = useGuardianChildren(guardianId);
  if (children.isPending) return <GuardianChildrenCardSkeleton />;
  const list = children.data ?? [];

  return (
    <Card className="p-5">
      <CardHeader title="Çocuklar" />
      {children.isError ? (
        <QueryError error={children.error} />
      ) : list.length === 0 ? (
        <EmptyState
          icon={Users}
          title="Çocuk yok"
          text="Bu veliye bağlı öğrenci yok. Bağlantı öğrencinin sayfasından kurulur."
        />
      ) : (
        <ul className="mt-2">
          {list.map((child) => (
            <li
              key={child.studentId}
              className="anim-fade border-t border-line py-3 first:border-t-0"
            >
              <Link
                to={userPath("student", child.studentId ?? "")}
                className="text-sm font-medium hover:underline"
              >
                {fullName(child)}
              </Link>
              <div className="text-[13px] text-dim">
                No {child.studentNumber} · {child.gradeLevel}. sınıf ·{" "}
                {RELATIONSHIP_LABELS[child.relationship ?? "OTHER"]}
                {child.active === false && " · pasif"}
              </div>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}
