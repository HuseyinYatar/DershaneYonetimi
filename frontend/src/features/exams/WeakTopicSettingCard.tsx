import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { useUpdateWeakTopicSetting, useWeakTopicSetting } from "@/api/queries/exam";
import { useBranchId } from "@/auth/useBranchId";
import { Card, CardHeader } from "@/components/ui/Card";
import { QueryError } from "@/components/ui/EmptyState";
import { Field, INPUT, PRIMARY_BUTTON } from "@/components/ui/form";
import { Skeleton } from "@/components/ui/Skeleton";
import {
  thresholdToPercent,
  toSettingUpdate,
  weakTopicSettingSchema,
  type WeakTopicSettingValues,
} from "@/lib/examForm";

export function WeakTopicSettingCardSkeleton() {
  return (
    <Card className="p-5">
      <CardHeader title="Zayıf konu ayarı" />
      <div role="status" aria-label="Yükleniyor" className="mt-4 flex flex-col gap-3">
        <Skeleton className="h-10" />
        <Skeleton className="h-10 w-32" />
      </div>
    </Card>
  );
}

function SettingForm({ branchId, initial }: { branchId: string; initial: WeakTopicSettingValues }) {
  const update = useUpdateWeakTopicSetting(branchId);
  const {
    register,
    handleSubmit,
    formState: { errors, isDirty },
  } = useForm<WeakTopicSettingValues>({
    resolver: zodResolver(weakTopicSettingSchema),
    defaultValues: initial,
  });

  return (
    <form
      onSubmit={handleSubmit((values) => update.mutate(toSettingUpdate(values)))}
      noValidate
      className="mt-4 flex flex-col gap-4"
    >
      <div className="grid gap-4 sm:grid-cols-2">
        <Field
          label="Son kaç deneme bakılsın"
          htmlFor="weak-window"
          error={errors.windowSize?.message}
        >
          <input
            id="weak-window"
            inputMode="numeric"
            className={INPUT}
            {...register("windowSize")}
          />
        </Field>
        <Field
          label="Başarı eşiği (%)"
          htmlFor="weak-threshold"
          error={errors.thresholdPercent?.message}
        >
          <input
            id="weak-threshold"
            inputMode="decimal"
            className={INPUT}
            {...register("thresholdPercent")}
          />
        </Field>
      </div>
      <div>
        <button type="submit" disabled={!isDirty || update.isPending} className={PRIMARY_BUTTON}>
          {update.isPending ? "Kaydediliyor…" : "Kaydet"}
        </button>
      </div>
    </form>
  );
}

// Branch setting: a topic is weak when its success rate over the last N published exams is below the threshold.
export function WeakTopicSettingCard() {
  const branchId = useBranchId();
  const setting = useWeakTopicSetting(branchId);

  if (!branchId || setting.isPending) return <WeakTopicSettingCardSkeleton />;
  if (setting.isError) {
    return (
      <Card className="p-5">
        <CardHeader title="Zayıf konu ayarı" />
        <QueryError error={setting.error} />
      </Card>
    );
  }
  const data = setting.data;

  return (
    <Card className="p-5">
      <CardHeader title="Zayıf konu ayarı" />
      <p className="mt-2 text-[13px] text-dim">
        {data
          ? "Ayar değişince şubedeki öğrencilerin zayıf konuları yeniden hesaplanır."
          : "Bu şube için ayar yok; kaydedilene kadar zayıf konu hesaplanmaz."}
      </p>
      {/* Keyed by the stored values so a save resets the dirty state. */}
      <SettingForm
        key={data ? `${data.windowSize}-${data.successThreshold}` : "none"}
        branchId={branchId}
        initial={{
          windowSize: data ? String(data.windowSize) : "",
          thresholdPercent: data ? thresholdToPercent(data.successThreshold) : "",
        }}
      />
    </Card>
  );
}
