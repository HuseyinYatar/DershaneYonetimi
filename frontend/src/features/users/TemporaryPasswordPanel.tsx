import { Check, Copy, KeyRound } from "lucide-react";
import { useState } from "react";
import { Card, CardHeader } from "@/components/ui/Card";
import { PillButton } from "@/components/ui/Pill";

// Shows a temporary password once. It lives only in the caller's component state: never in a query,
// storage, a toast or a log. Closing the panel (or leaving the page) loses it for good.
export function TemporaryPasswordPanel({
  password,
  onClose,
}: {
  password: string;
  onClose: () => void;
}) {
  const [copied, setCopied] = useState(false);

  const copy = () => {
    navigator.clipboard
      .writeText(password)
      .then(() => setCopied(true))
      .catch(() => setCopied(false));
  };

  return (
    <Card className="p-5">
      <CardHeader
        title="Geçici şifre"
        icon={<KeyRound size={18} strokeWidth={1.5} aria-hidden="true" />}
      />
      <p className="mt-3 text-[13px] text-dim">
        Bu şifre bir daha gösterilmez. Kullanıcıya iletin; ilk girişte değiştirmesi istenir.
      </p>
      <div className="mt-4 flex flex-wrap items-center gap-3">
        <code className="rounded-2xl bg-sub px-4 py-2 font-mono text-base select-all">
          {password}
        </code>
        <PillButton onClick={copy}>
          {copied ? (
            <Check size={14} strokeWidth={1.75} aria-hidden="true" />
          ) : (
            <Copy size={14} strokeWidth={1.5} aria-hidden="true" />
          )}
          {copied ? "Kopyalandı" : "Kopyala"}
        </PillButton>
        <PillButton onClick={onClose}>Kapat</PillButton>
      </div>
    </Card>
  );
}
