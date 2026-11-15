import { useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { useImportJob, useOpticalFormats, useUploadOpticalFile } from "@/api/queries/exam";
import type { components } from "@/api/schema/exam-analysis";
import { StatusBadge } from "@/components/StatusBadge";
import { Card, CardHeader } from "@/components/ui/Card";
import { QueryError } from "@/components/ui/EmptyState";
import { INPUT, PRIMARY_BUTTON } from "@/components/ui/form";
import { Skeleton } from "@/components/ui/Skeleton";
import { formatInt } from "@/lib/format";
import { jobBadge, jobIsRunning } from "@/lib/status/admin";

type ExamStatus = components["schemas"]["ExamStatus"];

export function ImportCardSkeleton() {
  return (
    <Card className="p-5">
      <CardHeader title="Optik dosya" />
      <div role="status" aria-label="Yükleniyor" className="mt-4 flex flex-col gap-3">
        <Skeleton className="h-10" />
        <Skeleton className="h-10 w-40" />
      </div>
    </Card>
  );
}

function JobStatus({ jobId }: { jobId: string }) {
  const queryClient = useQueryClient();
  const job = useImportJob(jobId);
  const finished = !!job.data && !jobIsRunning(job.data.status);

  // Results, ranks and the exam status change when the worker finishes: refresh the whole exam view once.
  useEffect(() => {
    if (finished) void queryClient.invalidateQueries({ queryKey: ["exam"] });
  }, [finished, queryClient]);

  if (job.isError) return <QueryError error={job.error} />;
  if (!job.data) {
    return (
      <div role="status" aria-label="Yükleniyor" className="mt-4">
        <Skeleton className="h-16" />
      </div>
    );
  }
  const badge = jobBadge(job.data.status);

  return (
    <div className="mt-4 rounded-2xl bg-sub p-4 text-sm">
      <div className="flex flex-wrap items-center gap-3">
        <StatusBadge tone={badge.tone} label={badge.label} />
        <span className="text-dim">
          {formatInt(job.data.succeededRows)} satır işlendi, {formatInt(job.data.failedRows)}{" "}
          eşleşmedi
          {job.data.totalRows > 0 && ` · toplam ${formatInt(job.data.totalRows)}`}
        </span>
      </div>
      {job.data.errorMessage && (
        <p className="mt-2 text-[13px] text-(--status-danger-fg)">{job.data.errorMessage}</p>
      )}
    </div>
  );
}

// The service has no "list jobs of an exam" call, so the job of the last upload is kept in the URL (?job=).
export function ImportCard({
  mockExamId,
  status,
  jobId,
  onJob,
}: {
  mockExamId: string;
  status: ExamStatus;
  jobId: string | null;
  onJob: (jobId: string) => void;
}) {
  const formats = useOpticalFormats();
  const upload = useUploadOpticalFile(mockExamId);
  const [formatId, setFormatId] = useState("");
  const [file, setFile] = useState<File>();

  if (formats.isPending) return <ImportCardSkeleton />;
  const canUpload = status === "KEY_READY" || status === "PROCESSED";

  return (
    <Card className="p-5">
      <CardHeader title="Optik dosya" />
      {formats.isError ? (
        <QueryError error={formats.error} />
      ) : status === "PUBLISHED" ? (
        <p className="mt-2 text-[13px] text-dim">
          Sonuçlar açıklandı; yeni dosya yüklenemez. Eksik öğrenciler eşleşmeyen satırlardan
          eklenir.
        </p>
      ) : !canUpload ? (
        <p className="mt-2 text-[13px] text-dim">
          Dosya yüklemek için önce cevap anahtarını kaydedin.
        </p>
      ) : (
        <div className="mt-4 flex flex-col gap-2 sm:flex-row sm:items-center">
          <select
            aria-label="Optik format"
            value={formatId}
            onChange={(e) => setFormatId(e.target.value)}
            className={`${INPUT} sm:max-w-56`}
          >
            <option value="">Optik format seç</option>
            {(formats.data ?? []).map((f) => (
              <option key={f.id} value={f.id}>
                {f.name}
              </option>
            ))}
          </select>
          <input
            type="file"
            aria-label="Optik okuyucu dosyası"
            accept=".txt,.dat,text/plain"
            onChange={(e) => setFile(e.target.files?.[0])}
            className="text-[13px]"
          />
          <button
            type="button"
            disabled={!formatId || !file || upload.isPending}
            onClick={() =>
              file &&
              upload.mutate(
                { file, opticalFormatId: formatId },
                { onSuccess: (job) => onJob(job.id) },
              )
            }
            className={PRIMARY_BUTTON}
          >
            {upload.isPending ? "Yükleniyor…" : "Yükle"}
          </button>
        </div>
      )}
      {jobId && <JobStatus jobId={jobId} />}
    </Card>
  );
}
