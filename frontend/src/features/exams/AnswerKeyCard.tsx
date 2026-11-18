import { KeyRound } from "lucide-react";
import { useState } from "react";
import {
  useAnswerKey,
  useCancelQuestion,
  useExamSubjects,
  useReplaceAnswerKey,
  useSubjectTopics,
} from "@/api/queries/exam";
import type { components } from "@/api/schema/exam-analysis";
import { StatusBadge } from "@/components/StatusBadge";
import { Card, CardHeader } from "@/components/ui/Card";
import { EmptyState, QueryError } from "@/components/ui/EmptyState";
import { PRIMARY_BUTTON, TEXTAREA } from "@/components/ui/form";
import { PillButton } from "@/components/ui/Pill";
import { useConfirm } from "@/lib/useConfirm";
import { Skeleton } from "@/components/ui/Skeleton";
import { parseAnswerKey } from "@/lib/answerKey";

type ExamStatus = components["schemas"]["ExamStatus"];

export function AnswerKeyCardSkeleton() {
  return (
    <Card className="p-5">
      <CardHeader title="Cevap anahtarı" />
      <div role="status" aria-label="Yükleniyor" className="mt-4 flex flex-col gap-3">
        <Skeleton className="h-24" />
        <Skeleton className="h-10 w-40" />
      </div>
    </Card>
  );
}

const SHOWN_ERRORS = 5;

// The whole key is replaced by one upload (only before an optical file is processed); afterwards a single
// question can be cancelled, which re-scores the results.
export function AnswerKeyCard({
  mockExamId,
  examTypeId,
  status,
}: {
  mockExamId: string;
  examTypeId: string;
  status: ExamStatus;
}) {
  const key = useAnswerKey(mockExamId);
  const subjects = useExamSubjects(examTypeId);
  const subjectList = subjects.data ?? [];
  const topics = useSubjectTopics(subjectList.map((s) => s.id));
  const replace = useReplaceAnswerKey(mockExamId);
  const ask = useConfirm();
  const cancel = useCancelQuestion(mockExamId);
  const [text, setText] = useState("");

  if (key.isPending || subjects.isPending || topics.isPending) return <AnswerKeyCardSkeleton />;
  if (key.isError) {
    return (
      <Card className="p-5">
        <CardHeader title="Cevap anahtarı" />
        <QueryError error={key.error} />
      </Card>
    );
  }

  const editable = status === "DRAFT" || status === "KEY_READY";
  const items = key.data ?? [];
  const subjectNames = new Map(subjectList.map((s) => [s.id, s.name]));
  const topicNames = new Map([...topics.topics.values()].flat().map((t) => [t.id, t.name]));
  const parsed = parseAnswerKey(
    text,
    subjectList.map((s) => ({ id: s.id, code: s.code, topics: topics.topics.get(s.id) ?? [] })),
  );

  return (
    <Card className="p-5">
      <CardHeader
        title={
          <>
            Cevap anahtarı <span className="font-normal text-dim">{items.length} soru</span>
          </>
        }
      />
      {editable ? (
        <div className="mt-4 flex flex-col gap-3">
          <p className="text-[13px] text-dim">
            Her satırda bir soru: kitapçık; soru no; ders kodu; konu; cevap (ör.
            A;1;TUR;Paragraf;B). Ders kodları: {subjectList.map((s) => s.code).join(", ")}.
            Kaydetmek mevcut anahtarın yerine geçer.
          </p>
          <input
            type="file"
            accept=".csv,.txt,text/plain,text/csv"
            aria-label="Cevap anahtarı dosyası"
            onChange={(e) => {
              void e.target.files?.[0]?.text().then(setText);
            }}
            className="text-[13px]"
          />
          <textarea
            aria-label="Cevap anahtarı"
            rows={6}
            value={text}
            onChange={(e) => setText(e.target.value)}
            className={`${TEXTAREA} font-mono`}
          />
          {parsed.errors.length > 0 && (
            <ul role="alert" className="text-[13px] text-(--status-danger-fg)">
              {parsed.errors.slice(0, SHOWN_ERRORS).map((error) => (
                <li key={error}>{error}</li>
              ))}
              {parsed.errors.length > SHOWN_ERRORS && (
                <li>… ve {parsed.errors.length - SHOWN_ERRORS} satır daha.</li>
              )}
            </ul>
          )}
          <div className="flex items-center gap-3">
            <button
              type="button"
              disabled={parsed.items.length === 0 || parsed.errors.length > 0 || replace.isPending}
              onClick={() =>
                replace.mutate(parsed.items, {
                  onSuccess: () => setText(""),
                })
              }
              className={PRIMARY_BUTTON}
            >
              {replace.isPending ? "Kaydediliyor…" : "Anahtarı kaydet"}
            </button>
            {parsed.items.length > 0 && (
              <span className="text-[13px] text-dim">{parsed.items.length} soru okundu</span>
            )}
          </div>
        </div>
      ) : (
        <p className="mt-2 text-[13px] text-dim">
          Sonuçlar işlendiği için anahtar değiştirilemez; hatalı bir soru iptal edilebilir ve
          sonuçlar yeniden hesaplanır.
        </p>
      )}
      {items.length === 0 ? (
        <EmptyState
          icon={KeyRound}
          title="Anahtar yok"
          text="Bu deneme için cevap anahtarı yüklenmemiş."
        />
      ) : (
        <ul className="mt-4 max-h-96 overflow-auto">
          {items.map((item) => (
            <li
              key={item.id}
              className="flex items-center justify-between gap-3 border-t border-line py-2 text-sm first:border-t-0"
            >
              <span className="min-w-0 truncate">
                <span className="tabular-nums text-dim">
                  {item.booklet}-{item.questionNo}
                </span>{" "}
                {subjectNames.get(item.subjectId) ?? "Ders"} ·{" "}
                {topicNames.get(item.topicId) ?? "Konu"} ·{" "}
                <b className="font-medium">{item.correctAnswer}</b>
              </span>
              {item.isCancelled ? (
                <StatusBadge tone="neutral" label="İptal" size="sm" />
              ) : (
                <PillButton
                  disabled={cancel.isPending}
                  onClick={async () => {
                    if (
                      await ask({
                        title: "Bu soru iptal edilsin mi?",
                        text: "Soru doğru/yanlış/boş sayılmaz, sonuçlar yeniden hesaplanır.",
                        confirmLabel: "İptal et",
                        danger: true,
                      })
                    ) {
                      cancel.mutate(item.id);
                    }
                  }}
                >
                  İptal et
                </PillButton>
              )}
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}
