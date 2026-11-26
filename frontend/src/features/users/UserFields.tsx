import type { Control, FieldErrors, UseFormRegister } from "react-hook-form";
import { useUserSubjects } from "@/api/queries/user";
import { Field, INPUT } from "@/components/ui/form";
import { FormSelect } from "@/components/ui/FormSelect";
import { GRADE_OPTIONS, type UserFormValues } from "@/lib/userForm";

// Form fields shared by the create page and the edit card; the kind decides the extra ones.
export function UserFields({
  kind,
  register,
  control,
  errors,
}: {
  kind: UserFormValues["kind"];
  register: UseFormRegister<UserFormValues>;
  control: Control<UserFormValues>;
  errors: FieldErrors<UserFormValues>;
}) {
  const subjects = useUserSubjects();
  return (
    <>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Ad" htmlFor="user-first-name" error={errors.firstName?.message}>
          <input id="user-first-name" maxLength={80} className={INPUT} {...register("firstName")} />
        </Field>
        <Field label="Soyad" htmlFor="user-last-name" error={errors.lastName?.message}>
          <input id="user-last-name" maxLength={80} className={INPUT} {...register("lastName")} />
        </Field>
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field
          label={kind === "teacher" ? "E-posta" : "E-posta (isteğe bağlı)"}
          htmlFor="user-email"
          error={errors.email?.message}
        >
          <input
            id="user-email"
            type="email"
            maxLength={254}
            className={INPUT}
            {...register("email")}
          />
        </Field>
        <Field
          label={kind === "guardian" ? "Telefon" : "Telefon (isteğe bağlı)"}
          htmlFor="user-phone"
          error={errors.phone?.message}
        >
          <input id="user-phone" type="tel" className={INPUT} {...register("phone")} />
        </Field>
      </div>
      {kind === "student" && (
        <div className="grid gap-4 sm:grid-cols-3">
          <Field
            label="Öğrenci numarası"
            htmlFor="user-student-number"
            error={errors.studentNumber?.message}
          >
            <input
              id="user-student-number"
              maxLength={20}
              className={INPUT}
              {...register("studentNumber")}
            />
          </Field>
          <Field label="Sınıf seviyesi" htmlFor="user-grade" error={errors.gradeLevel?.message}>
            <FormSelect
              id="user-grade"
              control={control}
              name="gradeLevel"
              options={GRADE_OPTIONS}
            />
          </Field>
          <Field label="Hedef sınav" htmlFor="user-target">
            <FormSelect
              id="user-target"
              control={control}
              name="targetExam"
              options={[
                { value: "YKS", label: "YKS" },
                { value: "LGS", label: "LGS" },
              ]}
            />
          </Field>
        </div>
      )}
      {kind === "teacher" && (
        <Field label="Branş" htmlFor="user-specialty" error={errors.specialtySubjectId?.message}>
          <FormSelect
            id="user-specialty"
            control={control}
            name="specialtySubjectId"
            loading={subjects.isPending}
            options={(subjects.data ?? [])
              .filter((s) => s.active)
              .map((s) => ({ value: s.id ?? "", label: s.name ?? "" }))}
          />
        </Field>
      )}
    </>
  );
}
