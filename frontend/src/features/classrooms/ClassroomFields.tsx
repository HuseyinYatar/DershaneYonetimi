import type { FieldErrors, UseFormRegister } from "react-hook-form";
import { Field, INPUT } from "@/components/ui/form";
import type { ClassroomFormValues } from "@/lib/classroomForm";

// `withGrade` is off when editing: a classroom's level cannot change.
export function ClassroomFields({
  register,
  errors,
  withGrade,
}: {
  register: UseFormRegister<ClassroomFormValues>;
  errors: FieldErrors<ClassroomFormValues>;
  withGrade: boolean;
}) {
  return (
    <div className="grid gap-4 sm:grid-cols-3">
      <Field label="Sınıf adı" htmlFor="classroom-name" error={errors.name?.message}>
        <input id="classroom-name" maxLength={60} className={INPUT} {...register("name")} />
      </Field>
      {withGrade && (
        <Field label="Seviye" htmlFor="classroom-grade" error={errors.gradeLevel?.message}>
          <select id="classroom-grade" className={INPUT} {...register("gradeLevel")}>
            <option value="">Seçin</option>
            {Array.from({ length: 13 }, (_, i) => i + 1).map((grade) => (
              <option key={grade} value={grade}>
                {grade}. sınıf
              </option>
            ))}
          </select>
        </Field>
      )}
      <Field label="Dönem" htmlFor="classroom-term" error={errors.term?.message}>
        <input
          id="classroom-term"
          placeholder="2026-2027"
          className={INPUT}
          {...register("term")}
        />
      </Field>
    </div>
  );
}
