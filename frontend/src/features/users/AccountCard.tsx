import { useState } from "react";
import {
  useDeactivateUser,
  useResetTemporaryPassword,
  useSendPasswordSetupEmail,
  type UserRow,
} from "@/api/queries/user";
import { Card, CardHeader } from "@/components/ui/Card";
import { PillButton } from "@/components/ui/Pill";
import { useConfirm } from "@/lib/useConfirm";
import { TemporaryPasswordPanel } from "./TemporaryPasswordPanel";

// Login account actions: password reset (by e-mail, or a temporary password without one) and deactivation.
export function AccountCard({ user }: { user: UserRow }) {
  const id = user.id ?? "";
  const sendEmail = useSendPasswordSetupEmail(id);
  const resetPassword = useResetTemporaryPassword(id);
  const ask = useConfirm();
  const deactivate = useDeactivateUser(id);
  // Page state only: the password is never cached, stored or toasted.
  const [password, setPassword] = useState<string>();

  const issuePassword = () =>
    resetPassword.mutate(undefined, {
      onSuccess: (result) => {
        setPassword(result.temporaryPassword);
        resetPassword.reset();
      },
    });

  return (
    <>
      {password && (
        <TemporaryPasswordPanel password={password} onClose={() => setPassword(undefined)} />
      )}
      <Card className="p-5">
        <CardHeader title="Hesap" />
        <p className="mt-2 text-[13px] text-dim">
          Giriş adı: <span className="text-ink">{user.username}</span>
        </p>
        {user.active === false ? (
          <p className="mt-4 text-sm">Hesap pasif; giriş kapalı. Kayıtlar korunuyor.</p>
        ) : (
          <div className="mt-4 flex flex-wrap gap-2">
            {user.email ? (
              <PillButton disabled={sendEmail.isPending} onClick={() => sendEmail.mutate()}>
                Şifre belirleme e-postası gönder
              </PillButton>
            ) : (
              <PillButton disabled={resetPassword.isPending} onClick={issuePassword}>
                Yeni geçici şifre
              </PillButton>
            )}
            <PillButton
              disabled={deactivate.isPending}
              onClick={async () => {
                if (
                  await ask({
                    title: "Hesap pasife alınsın mı?",
                    text: "Kullanıcı giriş yapamaz.",
                    confirmLabel: "Pasife al",
                    danger: true,
                  })
                ) {
                  deactivate.mutate();
                }
              }}
            >
              Pasife al
            </PillButton>
          </div>
        )}
      </Card>
    </>
  );
}
