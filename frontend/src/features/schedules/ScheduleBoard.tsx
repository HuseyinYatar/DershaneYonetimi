import { GripVertical, Wand2, X } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import {
  useBranchSchedules,
  useCreateSchedule,
  useCreateSchedules,
  useDeactivateSchedule,
  useMoveSchedule,
} from "@/api/queries/attendance";
import {
  useBranchClassrooms,
  useBranchTeachers,
  useClassroomsTeachers,
  useSetWeeklyHours,
  useUserSubjectNames,
} from "@/api/queries/user";
import type { components } from "@/api/schema/attendance";
import { useBranchId } from "@/auth/useBranchId";
import { Card } from "@/components/ui/Card";
import { QueryError } from "@/components/ui/EmptyState";
import { PillButton, SegButton } from "@/components/ui/Pill";
import { Select } from "@/components/ui/Select";
import { Skeleton } from "@/components/ui/Skeleton";
import { type Demand, planWeek } from "@/lib/scheduleAutoPlan";
import {
  overlaps,
  type Placed,
  SLOT_COUNT,
  SLOTS,
  type SlotSpan,
  spanOf,
  timesOf,
} from "@/lib/scheduleBoard";
import { DAY_LABELS, toCreateSchedule } from "@/lib/scheduleForm";
import { WEEKDAYS, type Weekday } from "@/lib/taskForm";
import { fullName } from "@/lib/userForm";
import { cn } from "@/lib/utils";

type Schedule = components["schemas"]["ScheduleResponse"];

// One lesson on the board: a saved schedule, or a draft the automatic distribution proposed.
interface Block extends Placed {
  classroomId: string;
  teacherId: string;
  subjectId: string;
  schedule?: Schedule;
}

// What is being dragged: a card from the palette (new lesson) or a block already on the board.
type Drag = { kind: "new"; teacherId: string; subjectId: string } | { kind: "move"; id: string };

const MAX_WEEKLY_HOURS = 40;
const today = () => new Date().toLocaleDateString("sv");
const sizeOf = (span: SlotSpan) => span.last - span.first + 1;

function hoursOf(blocks: Block[], classroomId: string, teacherId?: string, subjectId?: string) {
  return blocks
    .filter(
      (b) =>
        b.classroomId === classroomId && b.teacherId === teacherId && b.subjectId === subjectId,
    )
    .reduce((n, b) => n + sizeOf(b.span), 0);
}

export function ScheduleBoardSkeleton() {
  return (
    <Card className="mt-4 p-5">
      <div role="status" aria-label="Yükleniyor" className="flex flex-col gap-3">
        <Skeleton className="h-10 w-64" />
        <Skeleton className="h-64" />
      </div>
    </Card>
  );
}

export function ScheduleBoard() {
  const branchId = useBranchId();
  const classrooms = useBranchClassrooms();
  const teachers = useBranchTeachers();
  const subjects = useUserSubjectNames();
  const activeClassrooms = (classrooms.data ?? []).filter((c) => c.active);
  const assignments = useClassroomsTeachers(activeClassrooms.map((c) => c.id ?? ""));
  const schedules = useBranchSchedules();
  const [classroomId, setClassroomId] = useState("");
  const [duration, setDuration] = useState(1);
  const [validFrom, setValidFrom] = useState(today);
  const [showWeekend, setShowWeekend] = useState(false);
  const [drag, setDrag] = useState<Drag>();
  const [over, setOver] = useState("");
  const [drafts, setDrafts] = useState<Block[]>([]);
  const [unplaced, setUnplaced] = useState<string[]>([]);
  const create = useCreateSchedule();
  const saveDrafts = useCreateSchedules();
  const move = useMoveSchedule();
  const deactivate = useDeactivateSchedule();
  const setHours = useSetWeeklyHours();

  if (classrooms.isPending) return <ScheduleBoardSkeleton />;

  const teacherNames = new Map((teachers.data ?? []).map((t) => [t.id, fullName(t)]));
  const classroomNames = new Map((classrooms.data ?? []).map((c) => [c.id, c.name]));
  const existing: Block[] = (schedules.data ?? []).flatMap((s) => {
    const span = s.startsAt && s.endsAt ? spanOf(s.startsAt, s.endsAt) : undefined;
    return span && s.id && s.dayOfWeek
      ? [
          {
            id: s.id,
            dayOfWeek: s.dayOfWeek,
            span,
            classroomId: s.classroomId ?? "",
            teacherId: s.teacherId ?? "",
            subjectId: s.subjectId ?? "",
            schedule: s,
          },
        ]
      : [];
  });
  const blocks = [...existing, ...drafts];
  const mine = blocks.filter((b) => b.classroomId === classroomId);
  const hidden =
    (schedules.data ?? []).filter((s) => s.classroomId === classroomId).length -
    existing.filter((b) => b.classroomId === classroomId).length;
  const weekend =
    showWeekend || mine.some((b) => b.dayOfWeek === "SATURDAY" || b.dayOfWeek === "SUNDAY");
  const days = weekend ? WEEKDAYS : WEEKDAYS.slice(0, 5);
  const classroomAssignments = assignments.byClassroom.get(classroomId) ?? [];

  function drop(day: Weekday, slot: number) {
    setOver("");
    if (!drag || !classroomId || !branchId) return;
    const dragged = drag.kind === "move" ? blocks.find((b) => b.id === drag.id) : undefined;
    const teacherId = drag.kind === "new" ? drag.teacherId : dragged?.teacherId;
    if (!teacherId || (drag.kind === "move" && !dragged)) return;
    const span: SlotSpan = {
      first: slot,
      last: slot + (dragged ? sizeOf(dragged.span) : duration) - 1,
    };
    if (span.last >= SLOT_COUNT) {
      toast.error("Ders, programın saat aralığına sığmıyor.");
      return;
    }
    if (overlaps(mine, day, span, dragged?.id)) {
      toast.error("Bu saatlerde sınıfın başka bir dersi var.");
      return;
    }
    if (
      overlaps(
        blocks.filter((b) => b.teacherId === teacherId),
        day,
        span,
        dragged?.id,
      )
    ) {
      toast.error("Öğretmen bu saatlerde başka bir sınıfta ders veriyor.");
      return;
    }
    const times = timesOf(span);
    if (dragged) {
      if (dragged.dayOfWeek === day && dragged.span.first === span.first) return;
      const s = dragged.schedule;
      if (!s) {
        setDrafts((ds) =>
          ds.map((d) => (d.id === dragged.id ? { ...d, dayOfWeek: day, span } : d)),
        );
        return;
      }
      move.mutate({
        scheduleId: dragged.id,
        body: {
          teacherId: s.teacherId,
          subjectId: s.subjectId,
          dayOfWeek: day,
          ...times,
          validFrom: s.validFrom ?? validFrom,
          validTo: s.validTo,
        },
      });
    } else if (drag.kind === "new") {
      create.mutate({
        body: toCreateSchedule(
          {
            classroomId,
            teacherId: drag.teacherId,
            subjectId: drag.subjectId,
            dayOfWeek: day,
            ...times,
            validFrom,
            validTo: "",
          },
          branchId,
        ),
        idempotencyKey: crypto.randomUUID(),
      });
    }
  }

  // Distributes what every active classroom still needs, around the lessons that already exist.
  function autoPlan() {
    const demands: Demand[] = activeClassrooms.flatMap((c) =>
      (assignments.byClassroom.get(c.id ?? "") ?? []).map((a) => ({
        classroomId: c.id ?? "",
        teacherId: a.teacherId ?? "",
        subjectId: a.subjectId ?? "",
        hours: (a.weeklyHours ?? 0) - hoursOf(existing, c.id ?? "", a.teacherId, a.subjectId),
      })),
    );
    const plan = planWeek(
      demands,
      existing,
      days.map((d) => d.value),
    );
    setDrafts(plan.lessons.map((l) => ({ ...l, id: `draft-${crypto.randomUUID()}` })));
    setUnplaced(
      plan.missing.map(
        (m) =>
          `${classroomNames.get(m.classroomId)}: ${subjects.data?.get(m.subjectId) ?? "Ders"} · ${m.missing} saat`,
      ),
    );
    if (demands.every((d) => d.hours <= 0)) {
      toast.info("Yerleştirilecek ders yok. Sınıf seçip kartlardaki haftalık saatleri girin.");
    }
  }

  function saveDraft() {
    if (!branchId) return;
    saveDrafts.mutate(
      drafts.map((d) =>
        toCreateSchedule(
          {
            classroomId: d.classroomId,
            teacherId: d.teacherId,
            subjectId: d.subjectId,
            dayOfWeek: d.dayOfWeek as Weekday,
            ...timesOf(d.span),
            validFrom,
            validTo: "",
          },
          branchId,
        ),
      ),
      { onSettled: () => setDrafts([]) },
    );
  }

  function commitHours(a: (typeof classroomAssignments)[number], input: HTMLInputElement) {
    const hours = Number(input.value);
    if (!Number.isInteger(hours) || hours < 0 || hours > MAX_WEEKLY_HOURS) {
      toast.error(`Haftalık ders saati 0 ile ${MAX_WEEKLY_HOURS} arasında bir tam sayı olmalı.`);
      input.value = String(a.weeklyHours ?? 0);
      return;
    }
    if (hours === (a.weeklyHours ?? 0) || !a.teacherId || !a.assignmentId) return;
    setHours.mutate({ teacherId: a.teacherId, assignmentId: a.assignmentId, weeklyHours: hours });
  }

  // The state change runs after dragstart: changing the DOM inside it cancels the drag in Chrome.
  const startDrag = (next: Drag) => (e: React.DragEvent) => {
    e.dataTransfer.setData("text/plain", "");
    e.dataTransfer.effectAllowed = next.kind === "new" ? "copy" : "move";
    setTimeout(() => setDrag(next), 0);
  };
  const endDrag = () => {
    setDrag(undefined);
    setOver("");
  };

  return (
    <Card className="mt-4 p-5">
      <div className="flex flex-wrap items-end gap-3">
        {/* A div, not a label: a click on an option inside a label would re-click the trigger. */}
        <div className="flex flex-col gap-1 text-[13px] text-dim">
          <span>Sınıf</span>
          <Select
            aria-label="Sınıf"
            className="w-56"
            value={classroomId}
            onChange={setClassroomId}
            options={activeClassrooms.map((c) => ({ value: c.id ?? "", label: c.name ?? "" }))}
          />
        </div>
        <div className="flex flex-col gap-1 text-[13px] text-dim">
          <span>Elle ekleme süresi</span>
          <Select
            aria-label="Elle ekleme süresi"
            className="w-36"
            value={String(duration)}
            onChange={(value) => setDuration(Number(value))}
            options={[1, 2, 3, 4].map((n) => ({ value: String(n), label: `${n} ders saati` }))}
          />
        </div>
        <label className="flex flex-col gap-1 text-[13px] text-dim">
          Geçerli (başlangıç)
          <input
            type="date"
            className={INPUT}
            value={validFrom}
            onChange={(e) => setValidFrom(e.target.value)}
          />
        </label>
        <SegButton active={showWeekend} onClick={() => setShowWeekend(!showWeekend)}>
          Hafta sonu
        </SegButton>
        <PillButton
          disabled={assignments.pending || schedules.isPending || drafts.length > 0}
          onClick={autoPlan}
        >
          <Wand2 size={14} strokeWidth={1.75} aria-hidden="true" />
          Otomatik dağıt
        </PillButton>
      </div>

      {drafts.length > 0 && (
        <div className="mt-4 flex flex-wrap items-center gap-3 rounded-lg border border-dashed border-line bg-sub px-4 py-3 text-[13px]">
          <span>
            <span className="font-medium">Taslak:</span> {drafts.length} ders yerleştirildi.
            Kaydedene kadar programa işlenmez; sürükleyerek düzeltebilirsiniz.
          </span>
          <span className="ml-auto flex gap-2">
            <PillButton disabled={saveDrafts.isPending} onClick={saveDraft}>
              {saveDrafts.isPending ? "Kaydediliyor…" : "Taslağı kaydet"}
            </PillButton>
            <PillButton disabled={saveDrafts.isPending} onClick={() => setDrafts([])}>
              Vazgeç
            </PillButton>
          </span>
        </div>
      )}
      {unplaced.length > 0 && (
        <div className="mt-3 rounded-lg border border-line px-4 py-3 text-[13px]" role="status">
          <div className="font-medium">Şu dersler yerleşemedi (boş saat kalmadı):</div>
          <ul className="mt-1 list-disc pl-5 text-dim">
            {unplaced.map((u) => (
              <li key={u}>{u}</li>
            ))}
          </ul>
        </div>
      )}

      {!classroomId ? (
        <p className="mt-6 text-sm text-dim">
          Programı görmek veya haftalık ders saatlerini girmek için bir sınıf seçin.
        </p>
      ) : schedules.isError ? (
        <QueryError error={schedules.error} />
      ) : (
        <>
          <div className="mt-4 flex flex-wrap items-center gap-2">
            <span className="text-[13px] text-dim">
              Haftalık ders saatini girin, kartı programa sürükleyin:
            </span>
            {assignments.pending ? (
              <Skeleton className="h-8 w-40" />
            ) : classroomAssignments.length === 0 ? (
              <span className="text-[13px] text-dim">
                Bu sınıfa öğretmen atanmamış. Önce Kullanıcılar sayfasından atayın.
              </span>
            ) : (
              classroomAssignments.map((a) => {
                const required = a.weeklyHours ?? 0;
                const placed = hoursOf(blocks, classroomId, a.teacherId, a.subjectId);
                return (
                  <div
                    key={a.assignmentId}
                    className="flex items-center gap-2 rounded-full border border-line bg-white py-1 pr-3 pl-1"
                  >
                    <div
                      draggable
                      onDragStart={startDrag({
                        kind: "new",
                        teacherId: a.teacherId ?? "",
                        subjectId: a.subjectId ?? "",
                      })}
                      onDragEnd={endDrag}
                      className="flex cursor-grab items-center gap-1 rounded-full bg-brand-tint px-3 py-1 text-[13px] active:cursor-grabbing"
                    >
                      <GripVertical size={14} strokeWidth={1.5} aria-hidden="true" />
                      <span className="font-medium">{a.subjectName}</span>
                      <span className="text-dim">
                        {a.firstName} {a.lastName}
                      </span>
                    </div>
                    <input
                      type="number"
                      min={0}
                      max={MAX_WEEKLY_HOURS}
                      aria-label={`${a.subjectName} haftalık ders saati`}
                      key={`${a.assignmentId}-${required}`}
                      defaultValue={required}
                      onBlur={(e) => commitHours(a, e.currentTarget)}
                      onKeyDown={(e) => e.key === "Enter" && e.currentTarget.blur()}
                      className="h-7 w-14 rounded-md border border-line px-2 text-[13px]"
                    />
                    <span
                      className={cn("text-[13px]", placed < required ? "text-ink" : "text-dim")}
                    >
                      {placed}/{required} saat
                    </span>
                  </div>
                );
              })
            )}
          </div>

          <div className="mt-4 overflow-x-auto">
            <div
              className="grid min-w-[720px] border border-line"
              style={{
                gridTemplateColumns: `88px repeat(${days.length}, minmax(0, 1fr))`,
                gridTemplateRows: `auto repeat(${SLOT_COUNT}, minmax(56px, auto))`,
              }}
            >
              <div className="border-b border-line" />
              {days.map((d) => (
                <div
                  key={d.value}
                  className="border-b border-l border-line px-2 py-2 text-center text-[13px] font-medium"
                >
                  {DAY_LABELS[d.value]}
                </div>
              ))}
              {SLOTS.map((slot, i) => (
                <div
                  key={slot.startsAt}
                  style={{ gridRow: i + 2, gridColumn: 1 }}
                  className="border-b border-line px-2 py-2 text-[12px] font-medium"
                >
                  {slot.startsAt}-{slot.endsAt}
                </div>
              ))}
              {days.map((d, col) =>
                SLOTS.map((slot, i) => {
                  const key = `${d.value}-${i}`;
                  return (
                    <div
                      key={key}
                      style={{ gridRow: i + 2, gridColumn: col + 2 }}
                      onDragOver={(e) => {
                        if (!drag) return;
                        e.preventDefault();
                        setOver(key);
                      }}
                      onDragLeave={() => setOver((o) => (o === key ? "" : o))}
                      onDrop={(e) => {
                        e.preventDefault();
                        drop(d.value, i);
                      }}
                      className={cn(
                        "border-b border-l border-line",
                        over === key && "bg-brand-tint",
                      )}
                    />
                  );
                }),
              )}
              {mine.map((b) => {
                const col = days.findIndex((d) => d.value === b.dayOfWeek);
                if (col === -1) return null;
                const draft = !b.schedule;
                return (
                  <div
                    key={b.id}
                    draggable
                    onDragStart={startDrag({ kind: "move", id: b.id })}
                    onDragEnd={endDrag}
                    style={{
                      gridColumn: col + 2,
                      gridRow: `${b.span.first + 2} / span ${sizeOf(b.span)}`,
                    }}
                    className={cn(
                      "relative m-0.5 cursor-grab rounded-lg border p-2 pr-6 text-center text-[13px] active:cursor-grabbing",
                      draft ? "border-dashed border-ink/40 bg-white" : "border-line bg-brand-tint",
                      drag && "pointer-events-none",
                    )}
                  >
                    <div className="font-medium">{subjects.data?.get(b.subjectId) ?? "Ders"}</div>
                    <div className="text-dim">{teacherNames.get(b.teacherId) ?? "Öğretmen"}</div>
                    <button
                      type="button"
                      aria-label={draft ? "Taslak dersi kaldır" : "Dersi programdan kaldır"}
                      disabled={deactivate.isPending}
                      onClick={() => {
                        if (draft) {
                          setDrafts((ds) => ds.filter((d) => d.id !== b.id));
                        } else if (
                          window.confirm("Ders programdan kaldırılsın mı? Oluşmuş dersler kalır.")
                        ) {
                          deactivate.mutate(b.id);
                        }
                      }}
                      className="absolute top-1 right-1 grid size-5 place-items-center rounded-full hover:bg-sub"
                    >
                      <X size={12} strokeWidth={1.75} aria-hidden="true" />
                    </button>
                  </div>
                );
              })}
            </div>
          </div>
          {hidden > 0 && (
            <p className="mt-3 text-[13px] text-dim">
              {hidden} program 08:10–20:00 aralığı dışında olduğu için burada görünmüyor; Liste
              görünümünden düzenleyin.
            </p>
          )}
        </>
      )}
    </Card>
  );
}
