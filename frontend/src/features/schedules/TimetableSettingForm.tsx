import { zodResolver } from "@hookform/resolvers/zod";
import { useForm, useWatch } from "react-hook-form";
import { useSaveTimetableSetting } from "@/api/queries/attendance";
import { Field, INPUT, PRIMARY_BUTTON } from "@/components/ui/form";
import { FormTimePicker } from "@/components/ui/FormPickers";
import { PillButton } from "@/components/ui/Pill";
import { useConfirm } from "@/lib/useConfirm";
import type { TimetableSetting } from "@/lib/scheduleBoard";
import {
  previewOf,
  timetableFormSchema,
  toFormValues,
  toSetting,
  type TimetableFormValues,
} from "@/lib/timetableSetting";

// Lesson-hour grid of the branch. The stored draft holds clock times that may not fit a new grid, so
// saving one discards the draft.
export function TimetableSettingForm({
  branchId,
  setting,
  hasDrafts,
  onSaved,
  onDone,
}: {
  branchId: string;
  setting: TimetableSetting;
  hasDrafts: boolean;
  onSaved: () => void;
  onDone: () => void;
}) {
  const ask = useConfirm();
  const save = useSaveTimetableSetting(branchId);
  const {
    register,
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<TimetableFormValues>({
    resolver: zodResolver(timetableFormSchema),
    defaultValues: toFormValues(setting),
  });
  const preview = previewOf(useWatch({ control }) as TimetableFormValues);

  const onSubmit = handleSubmit(async (values) => {
    if (
      hasDrafts &&
      !(await ask({
        title: "Kaydedilmemiş taslak silinecek",
        text: "Kaydedilmemiş taslak ızgaraya uymayabilir; ders saatleri kaydedilince sunucudaki taslak atılır. Devam edilsin mi?",
        confirmLabel: "Devam et",
        danger: true,
      }))
    ) {
      return;
    }
    save.mutate(toSetting(values, setting.lunchLesson), {
      onSuccess: () => {
        onSaved();
        onDone();
      },
    });
  });

  return (
    <form
      onSubmit={onSubmit}
      noValidate
      className="mt-4 flex flex-col gap-4 rounded-2xl border border-line p-4"
    >
      <div className="grid gap-4 sm:grid-cols-4">
        <Field
          label="İlk ders başlangıcı"
          htmlFor="tt-start"
          error={errors.firstLessonStart?.message}
        >
          <FormTimePicker id="tt-start" control={control} name="firstLessonStart" />
        </Field>
        <Field label="Ders süresi (dk)" htmlFor="tt-lesson" error={errors.lessonMinutes?.message}>
          <input
            id="tt-lesson"
            type="number"
            inputMode="numeric"
            className={INPUT}
            {...register("lessonMinutes")}
          />
        </Field>
        <Field label="Teneffüs (dk)" htmlFor="tt-break" error={errors.breakMinutes?.message}>
          <input
            id="tt-break"
            type="number"
            inputMode="numeric"
            className={INPUT}
            {...register("breakMinutes")}
          />
        </Field>
        <Field label="Günlük ders sayısı" htmlFor="tt-count" error={errors.lessonCount?.message}>
          <input
            id="tt-count"
            type="number"
            inputMode="numeric"
            className={INPUT}
            {...register("lessonCount")}
          />
        </Field>
      </div>
      <p className="text-[13px] text-dim">
        Öğle arasını tablodaki "Öğle arası" kartını bir saate sürükleyerek yerleştirin.
      </p>
      <div className="flex flex-wrap items-center gap-3">
        <button type="submit" disabled={save.isPending} className={PRIMARY_BUTTON}>
          {save.isPending ? "Kaydediliyor…" : "Kaydet"}
        </button>
        <PillButton onClick={onDone} className="h-10 px-4">
          Vazgeç
        </PillButton>
        {preview && <span className="text-[13px] text-dim">{preview}</span>}
      </div>
    </form>
  );
}
