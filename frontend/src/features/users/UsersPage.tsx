import { ChevronLeft, ChevronRight, Plus, Users } from "lucide-react";
import { Link, useSearchParams } from "react-router";
import { useUserPage, useUserSubjects, type UserRow } from "@/api/queries/user";
import { StatusBadge } from "@/components/StatusBadge";
import { Card, CardHeader } from "@/components/ui/Card";
import { EmptyState, QueryError } from "@/components/ui/EmptyState";
import { PageHeader } from "@/components/ui/PageHeader";
import { PillLink, SegButton } from "@/components/ui/Pill";
import { SkeletonRows } from "@/components/ui/Skeleton";
import { TableHead, TableRow, Td, Th } from "@/components/ui/Table";
import { activeBadge } from "@/lib/status/admin";
import { KIND_LABELS, USER_KINDS, fullName, userPath, type UserKind } from "@/lib/userForm";

const COLS =
  "grid-cols-[minmax(0,1fr)_auto] md:grid-cols-[minmax(0,1.3fr)_minmax(0,1fr)_minmax(0,1fr)_110px]";
const PAGE_SIZE = 20;
const ACTIVE_FILTERS = [
  { key: "active", label: "Aktif", value: true },
  { key: "inactive", label: "Pasif", value: false },
  { key: "all", label: "Tümü", value: undefined },
] as const;

export function UsersPage() {
  const [params, setParams] = useSearchParams();
  const kind: UserKind = USER_KINDS.find((k) => k === params.get("kind")) ?? "student";
  const filter = ACTIVE_FILTERS.find((f) => f.key === params.get("state")) ?? ACTIVE_FILTERS[0];
  const page = Math.max(0, Number(params.get("page")) || 0);
  const users = useUserPage(kind, page, PAGE_SIZE, filter.value);
  const subjects = useUserSubjects();
  const subjectNames = new Map((subjects.data ?? []).map((s) => [s.id, s.name]));

  const update = (patch: Record<string, string | undefined>) => {
    const next = new URLSearchParams(params);
    for (const [key, value] of Object.entries(patch)) {
      if (value === undefined) next.delete(key);
      else next.set(key, value);
    }
    setParams(next, { replace: true });
  };

  const list = users.data?.content ?? [];
  const meta = users.data?.page;
  const totalPages = meta?.totalPages ?? 1;

  const detail = (row: UserRow) => {
    if ("studentNumber" in row) return `No ${row.studentNumber} · ${row.gradeLevel}. sınıf`;
    if ("specialtySubjectId" in row) return subjectNames.get(row.specialtySubjectId) ?? "Öğretmen";
    return row.phone ?? "—";
  };

  return (
    <>
      <PageHeader eyebrow="Şube kullanıcıları" title="Kullanıcılar" />
      <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
        <div role="group" aria-label="Kullanıcı türü" className="flex gap-1.5">
          {USER_KINDS.map((k) => (
            <SegButton
              key={k}
              active={kind === k}
              onClick={() => update({ kind: k, page: undefined })}
            >
              {KIND_LABELS[k].plural}
            </SegButton>
          ))}
        </div>
        <PillLink to={`/users/new?kind=${kind}`}>
          <Plus size={14} strokeWidth={1.75} aria-hidden="true" />
          Yeni {KIND_LABELS[kind].singular.toLocaleLowerCase("tr-TR")}
        </PillLink>
      </div>
      <Card className="mt-4 overflow-hidden">
        <CardHeader
          className="px-5 pt-5 pb-4"
          title={
            <>
              {KIND_LABELS[kind].plural}{" "}
              <span className="font-normal text-dim">{meta?.totalElements ?? ""}</span>
            </>
          }
          action={
            <div role="group" aria-label="Durum" className="flex gap-1.5">
              {ACTIVE_FILTERS.map((f) => (
                <SegButton
                  key={f.key}
                  active={filter.key === f.key}
                  onClick={() => update({ state: f.key, page: undefined })}
                >
                  {f.label}
                </SegButton>
              ))}
            </div>
          }
        />
        <TableHead cols={COLS}>
          <Th>Ad soyad</Th>
          <Th className="hidden md:flex">Bilgi</Th>
          <Th className="hidden md:flex">Giriş adı</Th>
          <Th>Durum</Th>
        </TableHead>
        {users.isError ? (
          <QueryError error={users.error} />
        ) : users.isPending ? (
          <div className="pt-4">
            <SkeletonRows rows={5} />
          </div>
        ) : list.length === 0 ? (
          <EmptyState
            icon={Users}
            title="Kullanıcı yok"
            text="Bu süzgeçle eşleşen kullanıcı görünmüyor."
          />
        ) : (
          list.map((row) => {
            const badge = activeBadge(row.active);
            return (
              <TableRow key={row.id} cols={COLS} className="anim-fade relative hover:bg-surface">
                <Td>
                  <Link
                    to={userPath(kind, row.id ?? "")}
                    className="block text-sm font-medium after:absolute after:inset-0 hover:underline"
                  >
                    {fullName(row)}
                  </Link>
                  <div className="text-[13px] text-dim md:hidden">{detail(row)}</div>
                </Td>
                <Td className="hidden text-sm md:block">{detail(row)}</Td>
                <Td className="hidden truncate text-sm md:block">{row.username}</Td>
                <Td>
                  <StatusBadge tone={badge.tone} label={badge.label} size="sm" />
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
                onClick={() => update({ page: String(page - 1) })}
                className="grid size-8 place-items-center rounded-full hover:bg-sub disabled:opacity-40"
              >
                <ChevronLeft size={16} strokeWidth={1.5} aria-hidden="true" />
              </button>
              <button
                type="button"
                aria-label="Sonraki sayfa"
                disabled={page + 1 >= totalPages}
                onClick={() => update({ page: String(page + 1) })}
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
