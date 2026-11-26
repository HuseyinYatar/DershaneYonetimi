import type { Control, FieldErrors, UseFormRegister } from "react-hook-form";
import { Field, INPUT } from "@/components/ui/form";
import { FormSelect } from "@/components/ui/FormSelect";
import { maskTerm, type ClassroomFormValues } from "@/lib/classroomForm";
import { GRADE_OPTIONS } from "@/lib/userForm";

// `withGrade` is off when editing: a classroom's level cannot change.
export function ClassroomFields({
  register,
  control,
  errors,
  withGrade,
}: {
  register: UseFormRegister<ClassroomFormValues>;
  control: Control<ClassroomFormValues>;
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
          <FormSelect
            id="classroom-grade"
            control={control}
            name="gradeLevel"
            options={GRADE_OPTIONS}
          />
        </Field>
      )}
      <Field label="Dönem" htmlFor="classroom-term" error={errors.term?.message}>
        <input
          id="classroom-term"
          placeholder="2026-2027"
          inputMode="numeric"
          maxLength={9}
          // onInput fires before RHF's onChange, so the form stores the masked value.
          onInput={(e) => (e.currentTarget.value = maskTerm(e.currentTarget.value))}
          className={INPUT}
          {...register("term")}
        />
      </Field>
    </div>
  );
}
