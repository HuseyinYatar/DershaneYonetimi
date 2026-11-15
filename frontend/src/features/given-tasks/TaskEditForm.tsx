import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import type { components } from "@/api/schema/task";
import { useUpdateTask } from "@/api/queries/task";
import { Field, INPUT, PRIMARY_BUTTON, TEXTAREA } from "@/components/ui/form";
import { PillButton } from "@/components/ui/Pill";
import { taskEditSchema, type TaskEditValues } from "@/lib/taskForm";

type Task = components["schemas"]["TaskResponse"];

// Only title and description can change; target, dates and recurrence are fixed once assignments exist.
export function TaskEditForm({ task, onDone }: { task: Task; onDone: () => void }) {
  const update = useUpdateTask(task.id!);
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<TaskEditValues>({
    resolver: zodResolver(taskEditSchema),
    defaultValues: { title: task.title ?? "", description: task.description ?? "" },
  });

  return (
    <form
      noValidate
      onSubmit={handleSubmit((values) =>
        update.mutate(
          {
            title: values.title.trim(),
            description: values.description.trim(),
            // Sent back unchanged: the update replaces the list.
            attachmentFileIds: task.attachmentFileIds,
          },
          { onSuccess: onDone },
        ),
      )}
      className="mt-4 flex flex-col gap-3"
    >
      <Field label="Başlık" htmlFor="edit-title" error={errors.title?.message}>
        <input id="edit-title" maxLength={200} className={INPUT} {...register("title")} />
      </Field>
      <Field label="Açıklama" htmlFor="edit-description" error={errors.description?.message}>
        <textarea
          id="edit-description"
          rows={4}
          maxLength={5000}
          className={TEXTAREA}
          {...register("description")}
        />
      </Field>
      <div className="flex gap-2">
        <button type="submit" disabled={update.isPending} className={PRIMARY_BUTTON}>
          {update.isPending ? "Kaydediliyor…" : "Kaydet"}
        </button>
        <PillButton onClick={onDone} className="h-10 px-4">
          Vazgeç
        </PillButton>
      </div>
    </form>
  );
}
