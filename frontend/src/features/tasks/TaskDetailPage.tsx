import { useEffect, useState, type FormEvent } from "react";
import { useParams } from "react-router";
import { useAssignment, useSubmitAssignment } from "@/api/queries/task";
import { ApiError } from "@/api/problem";
import { StatusBadge } from "@/components/StatusBadge";
import { Card, CardTitle } from "@/components/ui/Card";
import { QueryError } from "@/components/ui/EmptyState";
import { Breadcrumb, PageHeader } from "@/components/ui/PageHeader";
import { Skeleton } from "@/components/ui/Skeleton";
import { formatDeadline } from "@/lib/format";
import { assignmentBadge } from "@/lib/status/tasks";

const BACK = { label: "Görevlerim", to: "/tasks" };
// shortcut: fixed pause, the gateway sends no Retry-After; use the header if it ever does.
const RATE_LIMIT_COOLDOWN_MS = 10_000;

function SubmissionForm({
  assignmentId,
  existing,
  locked,
}: {
  assignmentId: string;
  existing: string;
  locked: boolean;
}) {
  const submit = useSubmitAssignment(assignmentId);
  const [draft, setDraft] = useState<string>();
  const [coolingDown, setCoolingDown] = useState(false);
  const value = draft ?? existing;
  // Nothing to send: the server rejects an empty submission, and an identical one only replaces itself.
  const nothingToSend = value.trim() === "" || value.trim() === existing.trim();

  useEffect(() => {
    if (!coolingDown) return;
    const timer = setTimeout(() => setCoolingDown(false), RATE_LIMIT_COOLDOWN_MS);
    return () => clearTimeout(timer);
  }, [coolingDown]);

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    // The empty case is rejected by the server (EMPTY_SUBMISSION) and shown through the global toast.
    submit.mutate(value.trim(), {
      onSuccess: () => setDraft(undefined),
      onError: (error) => {
        if (error instanceof ApiError && error.status === 429) setCoolingDown(true);
      },
    });
  };

  return (
    <form onSubmit={onSubmit} className="mt-4 flex flex-col gap-3">
      <label htmlFor="submission-description" className="text-[13px] text-dim">
        Açıklama
      </label>
      <textarea
        id="submission-description"
        rows={6}
        maxLength={4000}
        disabled={locked || submit.isPending}
        value={value}
        onChange={(e) => setDraft(e.target.value)}
        placeholder="Çalışmanla ilgili notunu yaz."
        className="w-full resize-y rounded-2xl border border-line bg-white p-3 text-sm disabled:bg-sub disabled:text-dim"
      />
      <div className="flex items-center gap-3">
        <button
          type="submit"
          disabled={locked || submit.isPending || coolingDown || nothingToSend}
          className="inline-flex h-10 items-center rounded-full bg-ink px-5 text-sm font-medium text-white disabled:opacity-50"
        >
          {submit.isPending ? "Gönderiliyor…" : existing ? "Teslimi güncelle" : "Teslim et"}
        </button>
        {locked && (
          <span className="text-[13px] text-dim">Bu görev için artık teslim yapılamaz.</span>
        )}
        {coolingDown && (
          <span role="status" className="text-[13px] text-dim">
            Çok hızlı denedin, birkaç saniye bekle.
          </span>
        )}
      </div>
    </form>
  );
}

export function TaskDetailPage() {
  const { assignmentId = "" } = useParams();
  const assignment = useAssignment(assignmentId);
  const data = assignment.data;

  if (assignment.isError) {
    return (
      <>
        <PageHeader breadcrumb={<Breadcrumb parent={BACK} current="Görev" />} title="Görev" />
        <QueryError error={assignment.error} className="py-16" />
      </>
    );
  }
  if (!data) {
    return (
      <>
        <PageHeader breadcrumb={<Breadcrumb parent={BACK} current="…" />} title="Görev" />
        <div role="status" aria-label="Yükleniyor" className="mt-6 flex flex-col gap-4">
          <Skeleton className="h-32" />
          <Skeleton className="h-64" />
        </div>
      </>
    );
  }

  const badge = assignmentBadge(data.status!);
  const locked = data.status === "GRADED" || data.status === "CANCELLED";
  const submission = data.submission;
  const evaluation = data.evaluation;

  return (
    <>
      <PageHeader
        breadcrumb={<Breadcrumb parent={BACK} current={data.taskTitle ?? "Görev"} />}
        eyebrow={data.dueAt ? `Son teslim ${formatDeadline(new Date(data.dueAt))}` : undefined}
        title={data.taskTitle ?? "Görev"}
      />
      <div className="mt-5 grid items-start gap-4 lg:grid-cols-2">
        <Card className="p-5">
          <div className="flex items-center justify-between gap-3">
            <CardTitle>Görev</CardTitle>
            <StatusBadge tone={badge.tone} label={badge.label} />
          </div>
          <p className="mt-4 text-sm leading-relaxed whitespace-pre-wrap">
            {data.taskDescription || "Açıklama yok."}
          </p>
          {evaluation && (
            <div className="mt-6 border-t border-line pt-4">
              <div className="text-[13px] text-dim">Değerlendirme</div>
              {evaluation.score !== undefined && (
                <div className="mt-1 text-[32px] leading-none font-light tracking-[-0.04em]">
                  {evaluation.score}
                  <span className="ml-1 text-base text-[#6b7280]">/100</span>
                </div>
              )}
              {evaluation.feedback && (
                <p className="mt-2 text-sm leading-snug">Öğretmen: “{evaluation.feedback}”</p>
              )}
            </div>
          )}
        </Card>
        <Card className="p-5">
          <CardTitle>Teslim</CardTitle>
          {submission?.submittedAt && (
            <p className="mt-1.5 text-[13px] text-dim">
              Teslim zamanı: {formatDeadline(new Date(submission.submittedAt))}
              {submission.late && " · geç teslim"}
            </p>
          )}
          <SubmissionForm
            key={`${data.id}-${submission?.submittedAt ?? ""}`}
            assignmentId={assignmentId}
            existing={submission?.description ?? ""}
            locked={locked}
          />
        </Card>
      </div>
    </>
  );
}
