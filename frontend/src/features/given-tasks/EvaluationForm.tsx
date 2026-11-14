import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { useEvaluateAssignment } from "@/api/queries/task";
import { Field, INPUT, PRIMARY_BUTTON, TEXTAREA } from "@/components/ui/form";
import { evaluationSchema, toEvaluationRequest, type EvaluationValues } from "@/lib/taskForm";

// Re-grading overwrites the previous evaluation, so the form starts with the saved values.
export function EvaluationForm({
  assignmentId,
  score,
  feedback,
}: {
  assignmentId: string;
  score?: number;
  feedback?: string;
}) {
  const evaluate = useEvaluateAssignment(assignmentId);
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<EvaluationValues>({
    resolver: zodResolver(evaluationSchema),
    defaultValues: { score: score === undefined ? "" : String(score), feedback: feedback ?? "" },
  });

  return (
    <form
      noValidate
      onSubmit={handleSubmit((values) => evaluate.mutate(toEvaluationRequest(values)))}
      className="flex flex-col gap-3"
    >
      <Field
        label="Puan (0–100)"
        htmlFor={`score-${assignmentId}`}
        error={errors.score?.message}
        className="sm:max-w-40"
      >
        <input
          id={`score-${assignmentId}`}
          inputMode="numeric"
          maxLength={3}
          className={INPUT}
          {...register("score")}
        />
      </Field>
      <Field
        label="Geri bildirim"
        htmlFor={`feedback-${assignmentId}`}
        error={errors.feedback?.message}
      >
        <textarea
          id={`feedback-${assignmentId}`}
          rows={3}
          maxLength={5000}
          className={TEXTAREA}
          {...register("feedback")}
        />
      </Field>
      <div>
        <button type="submit" disabled={evaluate.isPending} className={PRIMARY_BUTTON}>
          {evaluate.isPending
            ? "Kaydediliyor…"
            : score !== undefined || feedback
              ? "Değerlendirmeyi güncelle"
              : "Değerlendir"}
        </button>
      </div>
    </form>
  );
}
