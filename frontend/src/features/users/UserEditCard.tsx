import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { useUpdateUser, type UserRow } from "@/api/queries/user";
import { Card, CardHeader } from "@/components/ui/Card";
import { PRIMARY_BUTTON } from "@/components/ui/form";
import {
  EMPTY_USER_FORM,
  toUpdateRequest,
  userFormSchema,
  type UserFormValues,
  type UserKind,
} from "@/lib/userForm";
import { UserFields } from "./UserFields";

function toFormValues(kind: UserKind, user: UserRow): UserFormValues {
  return {
    ...EMPTY_USER_FORM,
    kind,
    firstName: user.firstName ?? "",
    lastName: user.lastName ?? "",
    email: user.email ?? "",
    phone: user.phone ?? "",
    ...("studentNumber" in user
      ? {
          studentNumber: user.studentNumber ?? "",
          gradeLevel: String(user.gradeLevel ?? ""),
          targetExam: user.targetExam ?? "YKS",
        }
      : {}),
    ...("specialtySubjectId" in user ? { specialtySubjectId: user.specialtySubjectId ?? "" } : {}),
  };
}

// The caller keys this card by the record's updatedAt, so a refetch after a save or a
// CONCURRENT_MODIFICATION error resets the form to the stored values.
export function UserEditCard({ kind, user }: { kind: UserKind; user: UserRow }) {
  const update = useUpdateUser(user.id ?? "");
  const {
    register,
    handleSubmit,
    formState: { errors, isDirty },
  } = useForm<UserFormValues>({
    resolver: zodResolver(userFormSchema),
    defaultValues: toFormValues(kind, user),
  });

  return (
    <Card className="p-5">
      <CardHeader title="Bilgiler" />
      <form
        onSubmit={handleSubmit((values) => update.mutate(toUpdateRequest(values)))}
        noValidate
        className="mt-4 flex flex-col gap-4"
      >
        <UserFields kind={kind} register={register} errors={errors} />
        <div>
          <button
            type="submit"
            disabled={!isDirty || update.isPending || user.active === false}
            className={PRIMARY_BUTTON}
          >
            {update.isPending ? "Kaydediliyor…" : "Kaydet"}
          </button>
        </div>
      </form>
    </Card>
  );
}
