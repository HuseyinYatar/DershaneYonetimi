import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useSearchParams } from "react-router";
import { useCreateUser, type CreatedUser } from "@/api/queries/user";
import { useBranchId } from "@/auth/useBranchId";
import { Card } from "@/components/ui/Card";
import { PRIMARY_BUTTON } from "@/components/ui/form";
import { Breadcrumb, PageHeader } from "@/components/ui/PageHeader";
import { PillLink, SegButton } from "@/components/ui/Pill";
import { useIdempotencyKey } from "@/lib/useIdempotencyKey";
import {
  EMPTY_USER_FORM,
  KIND_LABELS,
  USER_KINDS,
  toCreateRequest,
  userFormSchema,
  userPath,
  type UserFormValues,
  type UserKind,
} from "@/lib/userForm";
import { TemporaryPasswordPanel } from "./TemporaryPasswordPanel";
import { UserFields } from "./UserFields";

const BACK = { label: "Kullanıcılar", to: "/users" };

function CreateForm({
  kind,
  onCreated,
}: {
  kind: UserKind;
  onCreated: (created: CreatedUser) => void;
}) {
  const branchId = useBranchId();
  const create = useCreateUser();
  const idempotency = useIdempotencyKey();
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<UserFormValues>({
    resolver: zodResolver(userFormSchema),
    defaultValues: { ...EMPTY_USER_FORM, kind },
  });

  const onSubmit = handleSubmit((values) => {
    if (!branchId) return;
    create.mutate(
      { call: toCreateRequest(values, branchId), idempotencyKey: idempotency.key },
      {
        onSuccess: (created) => {
          // Move the (possible) temporary password out of the mutation cache into page state.
          onCreated(created);
          create.reset();
          idempotency.renew();
        },
        onError: idempotency.renewOnClientError,
      },
    );
  });

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
      <UserFields kind={kind} register={register} errors={errors} />
      <div>
        <button type="submit" disabled={create.isPending || !branchId} className={PRIMARY_BUTTON}>
          {create.isPending ? "Oluşturuluyor…" : "Hesabı oluştur"}
        </button>
      </div>
    </form>
  );
}

export function UserCreatePage() {
  const [params, setParams] = useSearchParams();
  const requested = params.get("kind");
  const kind: UserKind = USER_KINDS.find((k) => k === requested) ?? "student";
  const [created, setCreated] = useState<{ kind: UserKind; user: CreatedUser }>();
  // Kept apart from `created` so closing the panel drops the password but keeps the result.
  const [password, setPassword] = useState<string>();

  if (created) {
    const { user } = created;
    return (
      <>
        <PageHeader
          breadcrumb={<Breadcrumb parent={BACK} current="Yeni kullanıcı" />}
          title="Hesap oluşturuldu"
        />
        <div className="mt-6 flex max-w-2xl flex-col gap-4">
          {password && (
            <TemporaryPasswordPanel password={password} onClose={() => setPassword(undefined)} />
          )}
          <Card className="p-5 text-sm">
            {user.passwordSetupEmailSent && (
              <p>Kullanıcıya şifre belirleme e-postası gönderildi.</p>
            )}
            {user.temporaryPasswordAlreadyIssued && (
              <p>
                Bu hesap daha önce açılmış; geçici şifre tekrar gösterilmez. Yeni şifre için
                kullanıcı sayfasından "Yeni geçici şifre" isteyin.
              </p>
            )}
            {!user.passwordSetupEmailSent && !user.temporaryPasswordAlreadyIssued && !password && (
              <p>Geçici şifre kapatıldı. Gerekirse yeni bir şifre üretebilirsiniz.</p>
            )}
            <div className="mt-4 flex gap-2">
              <PillLink to={userPath(created.kind, user.id)}>Kullanıcıya git</PillLink>
              <button
                type="button"
                onClick={() => setCreated(undefined)}
                className="inline-flex h-8 items-center rounded-full border border-line bg-white px-3 text-[13px] font-medium hover:border-[#cfd2d9]"
              >
                Yeni kullanıcı ekle
              </button>
            </div>
          </Card>
        </div>
      </>
    );
  }

  return (
    <>
      <PageHeader
        breadcrumb={<Breadcrumb parent={BACK} current="Yeni kullanıcı" />}
        title="Yeni kullanıcı"
      />
      <Card className="mt-6 max-w-2xl p-5">
        <div role="group" aria-label="Kullanıcı türü" className="mb-5 flex gap-1.5">
          {USER_KINDS.map((k) => (
            <SegButton
              key={k}
              active={kind === k}
              onClick={() => setParams({ kind: k }, { replace: true })}
            >
              {KIND_LABELS[k].singular}
            </SegButton>
          ))}
        </div>
        <CreateForm
          key={kind}
          kind={kind}
          onCreated={(user) => {
            setPassword(user.temporaryPassword);
            setCreated({ kind, user: { ...user, temporaryPassword: undefined } });
          }}
        />
      </Card>
    </>
  );
}
