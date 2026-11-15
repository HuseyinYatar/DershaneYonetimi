import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router";
import { useCreateMockExam, useExamTypes } from "@/api/queries/exam";
import { useBranchId } from "@/auth/useBranchId";
import { Card, CardHeader } from "@/components/ui/Card";
import { Field, INPUT, PRIMARY_BUTTON } from "@/components/ui/form";
import { PillButton } from "@/components/ui/Pill";
import { Skeleton } from "@/components/ui/Skeleton";
import {
  SCORING_SOURCE_OPTIONS,
  mockExamFormSchema,
  toCreateMockExam,
  type MockExamFormValues,
} from "@/lib/examForm";

export function NewExamFormSkeleton() {
  return (
    <Card className="p-5">
      <div role="status" aria-label="Yükleniyor" className="flex flex-col gap-4">
        <Skeleton className="h-6 w-40" />
        <Skeleton className="h-10" />
        <Skeleton className="h-10" />
      </div>
    </Card>
  );
}

// Creates a DRAFT exam; the answer key is uploaded on its page next.
export function NewExamForm({ onDone }: { onDone: () => void }) {
  const navigate = useNavigate();
  const branchId = useBranchId();
  const examTypes = useExamTypes();
  const create = useCreateMockExam();
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<MockExamFormValues>({
    resolver: zodResolver(mockExamFormSchema),
    defaultValues: {
      examTypeId: "",
      name: "",
      publisher: "",
      examDate: "",
      scoringSource: "INSTITUTIONAL",
    },
  });

  if (examTypes.isPending) return <NewExamFormSkeleton />;

  const onSubmit = handleSubmit((values) => {
    if (!branchId) return;
    create.mutate(toCreateMockExam(values, branchId), {
      onSuccess: (exam) => navigate(`/exams/${exam.id}`),
    });
  });

  return (
    <Card className="p-5">
      <CardHeader title="Yeni deneme" />
      <form onSubmit={onSubmit} noValidate className="mt-4 flex flex-col gap-4">
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Deneme adı" htmlFor="exam-name" error={errors.name?.message}>
            <input id="exam-name" maxLength={200} className={INPUT} {...register("name")} />
          </Field>
          <Field
            label="Yayınevi (isteğe bağlı)"
            htmlFor="exam-publisher"
            error={errors.publisher?.message}
          >
            <input
              id="exam-publisher"
              maxLength={200}
              className={INPUT}
              {...register("publisher")}
            />
          </Field>
        </div>
        <div className="grid gap-4 sm:grid-cols-3">
          <Field label="Sınav türü" htmlFor="exam-type" error={errors.examTypeId?.message}>
            <select id="exam-type" className={INPUT} {...register("examTypeId")}>
              <option value="">Seçin</option>
              {(examTypes.data ?? []).map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Tarih" htmlFor="exam-date" error={errors.examDate?.message}>
            <input id="exam-date" type="date" className={INPUT} {...register("examDate")} />
          </Field>
          <Field label="Puanlama" htmlFor="exam-scoring">
            <select id="exam-scoring" className={INPUT} {...register("scoringSource")}>
              {SCORING_SOURCE_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
          </Field>
        </div>
        <div className="flex gap-2">
          <button type="submit" disabled={create.isPending || !branchId} className={PRIMARY_BUTTON}>
            {create.isPending ? "Oluşturuluyor…" : "Denemeyi oluştur"}
          </button>
          <PillButton onClick={onDone} className="h-10 px-4">
            Vazgeç
          </PillButton>
        </div>
      </form>
    </Card>
  );
}
