import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { useExamSubjects, useSetNationalStatistics, useStatistics } from "@/api/queries/exam";
import { Card, CardHeader } from "@/components/ui/Card";
import { QueryError } from "@/components/ui/EmptyState";
import { INPUT, PRIMARY_BUTTON } from "@/components/ui/form";
import { Skeleton } from "@/components/ui/Skeleton";
import { nationalStatsSchema, toNationalItems, type NationalStatsValues } from "@/lib/examForm";

export function NationalStatisticsCardSkeleton() {
  return (
    <Card className="p-5">
      <CardHeader title="Türkiye geneli istatistikler" />
      <div role="status" aria-label="Yükleniyor" className="mt-4 flex flex-col gap-3">
        <Skeleton className="h-10" />
        <Skeleton className="h-10" />
        <Skeleton className="h-10 w-32" />
      </div>
    </Card>
  );
}

const DECIMAL = (value: string | number | undefined) =>
  value === undefined ? "" : String(Number(value)).replace(".", ",");

function StatsForm({
  mockExamId,
  rows,
}: {
  mockExamId: string;
  rows: {
    subjectId: string;
    name: string;
    mean: string;
    stdDev: string;
    participantCount: string;
  }[];
}) {
  const save = useSetNationalStatistics(mockExamId);
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<NationalStatsValues>({
    resolver: zodResolver(nationalStatsSchema),
    defaultValues: { rows },
  });

  return (
    <form
      onSubmit={handleSubmit((values) => save.mutate(toNationalItems(values)))}
      noValidate
      className="mt-4 flex flex-col gap-3"
    >
      <div className="hidden grid-cols-[minmax(0,1fr)_110px_110px_130px] gap-2 text-[13px] text-dim md:grid">
        <span>Ders</span>
        <span>Ortalama</span>
        <span>Standart sapma</span>
        <span>Katılımcı</span>
      </div>
      {rows.map((row, i) => (
        <div
          key={row.subjectId}
          className="grid grid-cols-3 items-start gap-2 md:grid-cols-[minmax(0,1fr)_110px_110px_130px]"
        >
          <span className="col-span-3 pt-2 text-sm md:col-span-1">{row.name}</span>
          <input type="hidden" {...register(`rows.${i}.subjectId`)} />
          <div>
            <input
              aria-label={`${row.name} ortalama`}
              inputMode="decimal"
              className={INPUT}
              {...register(`rows.${i}.mean`)}
            />
            {errors.rows?.[i]?.mean && (
              <p role="alert" className="mt-1 text-[13px] text-(--status-danger-fg)">
                {errors.rows[i].mean.message}
              </p>
            )}
          </div>
          <div>
            <input
              aria-label={`${row.name} standart sapma`}
              inputMode="decimal"
              className={INPUT}
              {...register(`rows.${i}.stdDev`)}
            />
            {errors.rows?.[i]?.stdDev && (
              <p role="alert" className="mt-1 text-[13px] text-(--status-danger-fg)">
                {errors.rows[i].stdDev.message}
              </p>
            )}
          </div>
          <div>
            <input
              aria-label={`${row.name} katılımcı sayısı`}
              inputMode="numeric"
              className={INPUT}
              {...register(`rows.${i}.participantCount`)}
            />
            {errors.rows?.[i]?.participantCount && (
              <p role="alert" className="mt-1 text-[13px] text-(--status-danger-fg)">
                {errors.rows[i].participantCount.message}
              </p>
            )}
          </div>
        </div>
      ))}
      {errors.rows?.message && (
        <p role="alert" className="text-[13px] text-(--status-danger-fg)">
          {errors.rows.message}
        </p>
      )}
      <div>
        <button type="submit" disabled={save.isPending} className={PRIMARY_BUTTON}>
          {save.isPending ? "Kaydediliyor…" : "İstatistikleri kaydet"}
        </button>
      </div>
    </form>
  );
}

// Publisher-announced nationwide mean and standard deviation per subject. Saving replaces all national
// rows; for a NATIONAL exam the estimated scores are recalculated at once.
export function NationalStatisticsCard({
  mockExamId,
  examTypeId,
}: {
  mockExamId: string;
  examTypeId: string;
}) {
  const subjects = useExamSubjects(examTypeId);
  const stats = useStatistics(mockExamId);

  if (subjects.isPending || stats.isPending) return <NationalStatisticsCardSkeleton />;
  if (subjects.isError || stats.isError) {
    return (
      <Card className="p-5">
        <CardHeader title="Türkiye geneli istatistikler" />
        <QueryError error={subjects.error ?? stats.error} />
      </Card>
    );
  }
  const national = new Map(
    (stats.data ?? []).filter((s) => s.source === "NATIONAL").map((s) => [s.subjectId, s]),
  );
  const rows = (subjects.data ?? []).map((s) => ({
    subjectId: s.id,
    name: s.name,
    mean: DECIMAL(national.get(s.id)?.mean),
    stdDev: DECIMAL(national.get(s.id)?.stdDev),
    participantCount: String(national.get(s.id)?.participantCount ?? ""),
  }));
  // Keyed by the stored values so a save resets the inputs to what the server holds.
  const stamp = [...national.values()].map((s) => s.updatedAt).join();

  return (
    <Card className="p-5">
      <CardHeader title="Türkiye geneli istatistikler" />
      <p className="mt-2 text-[13px] text-dim">
        Yayınevi veya sınav kurumu açıkladıktan sonra girin. Boş bırakılan derslerin istatistiği
        kaydedilmez.
      </p>
      <StatsForm key={stamp} mockExamId={mockExamId} rows={rows} />
    </Card>
  );
}
