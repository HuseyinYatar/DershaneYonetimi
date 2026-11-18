import { ChevronLeft, ChevronRight, FileCheck } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router";
import { useDecideExcuse, useExcuses } from "@/api/queries/attendance";
import type { components } from "@/api/schema/attendance";
import { useBranchStudents } from "@/api/queries/user";
import { StatusBadge } from "@/components/StatusBadge";
import { Card, CardHeader } from "@/components/ui/Card";
import { EmptyState, QueryError } from "@/components/ui/EmptyState";
import { Breadcrumb, PageHeader } from "@/components/ui/PageHeader";
import { PillButton, SegButton } from "@/components/ui/Pill";
import { useConfirm } from "@/lib/useConfirm";
import { SkeletonRows } from "@/components/ui/Skeleton";
import { TableHead, TableRow, Td, Th } from "@/components/ui/Table";
import { formatDayMonth, parseDateOnly } from "@/lib/format";
import { excuseBadge } from "@/lib/status/attendance";
import { fullName, userPath } from "@/lib/userForm";

type ExcuseStatus = NonNullable<components["schemas"]["ExcuseResponse"]["status"]>;

const BACK = { label: "Yoklama", to: "/sessions" };
const COLS =
  "grid-cols-[minmax(0,1fr)_auto] md:grid-cols-[minmax(0,1fr)_150px_minmax(0,1.3fr)_120px_150px]";
const PAGE_SIZE = 20;
// Pending first: it is the default view, the other states are one click away.
const FILTERS: { label: string; value: ExcuseStatus | undefined }[] = [
  { label: "Bekleyen", value: "PENDING" },
  { label: "Onaylanan", value: "APPROVED" },
  { label: "Reddedilen", value: "REJECTED" },
  { label: "Tümü", value: undefined },
];

const range = (from?: string, to?: string) => {
  const start = formatDayMonth(parseDateOnly(from ?? ""));
  return from === to ? start : `${start} – ${formatDayMonth(parseDateOnly(to ?? ""))}`;
};

export function ExcusesPage() {
  const [status, setStatus] = useState<ExcuseStatus | undefined>("PENDING");
  const [page, setPage] = useState(0);
  const excuses = useExcuses(status, page, PAGE_SIZE);
  const students = useBranchStudents();
  const ask = useConfirm();
  const decide = useDecideExcuse();
  const names = new Map((students.data ?? []).map((s) => [s.id, fullName(s)]));
  const list = excuses.data?.content ?? [];
  const totalPages = excuses.data?.page?.totalPages ?? 1;

  return (
    <>
      <PageHeader
        breadcrumb={<Breadcrumb parent={BACK} current="Mazeretler" />}
        title="Mazeretler"
      />
      <Card className="mt-6 overflow-hidden">
        <CardHeader
          className="px-5 pt-5 pb-4"
          title={
            <>
              Mazeret bildirimleri{" "}
              <span className="font-normal text-dim">
                {excuses.data?.page?.totalElements ?? ""}
              </span>
            </>
          }
          action={
            <div role="group" aria-label="Duruma göre süz" className="flex flex-wrap gap-1.5">
              {FILTERS.map((f) => (
                <SegButton
                  key={f.label}
                  active={status === f.value}
                  onClick={() => {
                    setStatus(f.value);
                    setPage(0);
                  }}
                >
                  {f.label}
                </SegButton>
              ))}
            </div>
          }
        />
        <TableHead cols={COLS}>
          <Th>Öğrenci</Th>
          <Th className="hidden md:flex">Tarih</Th>
          <Th className="hidden md:flex">Neden</Th>
          <Th className="hidden md:flex">Durum</Th>
          <Th />
        </TableHead>
        {excuses.isError ? (
          <QueryError error={excuses.error} />
        ) : excuses.isPending ? (
          <div className="pt-4">
            <SkeletonRows rows={4} />
          </div>
        ) : list.length === 0 ? (
          <EmptyState
            icon={FileCheck}
            title="Mazeret yok"
            text={
              status === "PENDING"
                ? "Karar bekleyen mazeret bildirimi yok."
                : "Bu süzgeçle eşleşen mazeret görünmüyor."
            }
          />
        ) : (
          list.map((excuse) => {
            const badge = excuseBadge(excuse.status ?? "PENDING");
            const id = excuse.id ?? "";
            return (
              <TableRow key={id} cols={COLS} className="anim-fade">
                <Td>
                  <Link
                    to={userPath("student", excuse.studentId ?? "")}
                    className="text-sm font-medium hover:underline"
                  >
                    {names.get(excuse.studentId) ?? "Öğrenci"}
                  </Link>
                  <div className="text-[13px] text-dim md:hidden">
                    {range(excuse.fromDate, excuse.toDate)}
                  </div>
                </Td>
                <Td className="hidden text-sm md:block">{range(excuse.fromDate, excuse.toDate)}</Td>
                <Td className="hidden text-sm md:block">{excuse.reason}</Td>
                <Td className="hidden md:block">
                  <StatusBadge tone={badge.tone} label={badge.label} size="sm" />
                </Td>
                <Td className="flex justify-end gap-2">
                  {excuse.status === "PENDING" ? (
                    <>
                      <PillButton
                        disabled={decide.isPending}
                        onClick={() => decide.mutate({ excuseId: id, approve: true })}
                      >
                        Onayla
                      </PillButton>
                      <PillButton
                        disabled={decide.isPending}
                        onClick={async () => {
                          if (
                            await ask({
                              title: "Mazeret reddedilsin mi?",
                              text: "Karar sonradan değişmez.",
                              confirmLabel: "Reddet",
                              danger: true,
                            })
                          ) {
                            decide.mutate({ excuseId: id, approve: false });
                          }
                        }}
                      >
                        Reddet
                      </PillButton>
                    </>
                  ) : (
                    <span className="md:hidden">
                      <StatusBadge tone={badge.tone} label={badge.label} iconOnly />
                    </span>
                  )}
                </Td>
              </TableRow>
            );
          })
        )}
        {totalPages > 1 && (
          <div className="flex items-center justify-between border-t border-line px-5 py-3 text-[13px] text-dim">
            <span>
              Sayfa {page + 1} / {totalPages}
            </span>
            <span className="flex gap-1">
              <button
                type="button"
                aria-label="Önceki sayfa"
                disabled={page === 0}
                onClick={() => setPage(page - 1)}
                className="grid size-8 place-items-center rounded-full hover:bg-sub disabled:opacity-40"
              >
                <ChevronLeft size={16} strokeWidth={1.5} aria-hidden="true" />
              </button>
              <button
                type="button"
                aria-label="Sonraki sayfa"
                disabled={page + 1 >= totalPages}
                onClick={() => setPage(page + 1)}
                className="grid size-8 place-items-center rounded-full hover:bg-sub disabled:opacity-40"
              >
                <ChevronRight size={16} strokeWidth={1.5} aria-hidden="true" />
              </button>
            </span>
          </div>
        )}
      </Card>
    </>
  );
}
