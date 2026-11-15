import { useNavigate } from "react-router";
import { useMarkAllRead, useMarkRead, useNotifications } from "@/api/queries/notification";
import { useActor } from "@/auth/useActor";
import { Card } from "@/components/ui/Card";
import { EmptyState, QueryError } from "@/components/ui/EmptyState";
import { PageHeader } from "@/components/ui/PageHeader";
import { PillButton } from "@/components/ui/Pill";
import { SkeletonRows } from "@/components/ui/Skeleton";
import { formatDeadline } from "@/lib/format";
import { cn } from "@/lib/utils";

// The notification payload has no link field, so the destination is derived from the event type.
// Exam notifications go to the results list (the e-mail link carries the exam id, the in-app item does not).
export function notificationTarget(eventType: string, studentId: string): string | null {
  switch (eventType) {
    case "TaskAssigned":
    case "TaskDueSoon":
    case "TaskOverdue":
    case "SubmissionGraded":
      return "/tasks";
    case "StudentAbsent":
    case "StudentExcusedByTeacher":
      return "/attendance";
    case "ExamResultsPublished":
    case "StudentResultAdded":
      return `/students/${studentId}/results`;
    default:
      return null;
  }
}

export function NotificationsPage() {
  const actor = useActor();
  const navigate = useNavigate();
  const notifications = useNotifications();
  const markRead = useMarkRead();
  const markAll = useMarkAllRead();
  const list = notifications.data ?? [];
  const hasUnread = list.some((n) => n.readAt === null);

  return (
    <>
      <PageHeader eyebrow="Son 50 bildirim" title="Bildirimler" />
      <div className="mt-4">
        <PillButton disabled={!hasUnread || markAll.isPending} onClick={() => markAll.mutate()}>
          Tümünü okundu işaretle
        </PillButton>
      </div>
      <Card className="mt-4">
        {notifications.isError ? (
          <QueryError error={notifications.error} />
        ) : notifications.isPending ? (
          <div className="pt-5">
            <SkeletonRows rows={4} />
          </div>
        ) : list.length === 0 ? (
          <EmptyState text="Henüz bildirimin yok." />
        ) : (
          <ul>
            {list.map((item) => {
              const unread = item.readAt === null;
              const target = actor ? notificationTarget(item.eventType, actor.id) : null;
              return (
                <li key={item.id} className="border-t border-line first:border-t-0">
                  <button
                    type="button"
                    onClick={() => {
                      if (unread) markRead.mutate(item.id);
                      if (target) void navigate(target);
                    }}
                    className="flex w-full items-start gap-3 px-5 py-4 text-left hover:bg-surface"
                  >
                    <span
                      aria-hidden="true"
                      className={cn(
                        "mt-2 size-2 shrink-0 rounded-full",
                        unread ? "bg-brand" : "bg-transparent",
                      )}
                    />
                    <span className="min-w-0 flex-1">
                      <span
                        className={cn(
                          "block text-sm leading-snug",
                          unread ? "font-medium" : "text-dim",
                        )}
                      >
                        {item.title}
                        {unread && <span className="sr-only"> (okunmadı)</span>}
                      </span>
                      <span className="mt-0.5 block text-[13px] leading-snug text-dim">
                        {item.body}
                      </span>
                    </span>
                    <span className="shrink-0 text-[13px] text-dim">
                      {formatDeadline(new Date(item.createdAt))}
                    </span>
                  </button>
                </li>
              );
            })}
          </ul>
        )}
      </Card>
    </>
  );
}
