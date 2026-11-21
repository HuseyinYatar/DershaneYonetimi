import { Clock, GripVertical, Utensils, Wand2, X } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import {
  useBranchSchedules,
  useCommitDraft,
  useCreateSchedule,
  useCurrentDraft,
  useDeactivateSchedules,
  useDeleteDraftLessons,
  useDiscardDraft,
  useGenerateDraft,
  useMoveDraftLessons,
  useMoveSchedules,
  useSaveTimetableSetting,
  useTimetableSetting,
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
import { DatePicker } from "@/components/ui/DatePicker";
import { PillButton, SegButton } from "@/components/ui/Pill";
import { useConfirm } from "@/lib/useConfirm";
import { Select } from "@/components/ui/Select";
import { Skeleton } from "@/components/ui/Skeleton";
import {
  DEFAULT_TIMETABLE,
  buildSlots,
  overlaps,
  type Placed,
  type SlotSpan,
  spanOf,
  timesOf,
} from "@/lib/scheduleBoard";
import { DAY_LABELS, toCreateSchedule } from "@/lib/scheduleForm";
import { WEEKDAYS, type Weekday } from "@/lib/taskForm";
import { draftToBlocks } from "@/lib/timetableDraft";
import { fullName } from "@/lib/userForm";
import { cn } from "@/lib/utils";
import { findConflicts, findLunchConflicts } from "@/lib/scheduleConflicts";
import { mergeAdjacent } from "@/lib/scheduleGroups";
import { TimetableSettingForm } from "./TimetableSettingForm";
import { WeekOverviewGrid } from "./WeekOverviewGrid";

type Schedule = components["schemas"]["ScheduleResponse"];

// One lesson on the board: a saved schedule, or a lesson of the stored draft (no `schedule`).
interface Block extends Placed {
  classroomId: string;
  teacherId: string;
  subjectId: string;
  schedule?: Schedule;
}

// What is being dragged: a card from the palette (new lesson) or a block already on the board.
type Drag =
  | { kind: "new"; classroomId: string; teacherId: string; subjectId: string }
  | { kind: "move"; id: string }
  | { kind: "lunch" };

// Value of the classroom picker for the whole-branch overview (branch admins only: it needs their branch).
const ALL = "__all__";
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
  const callerBranchId = useBranchId();
  const classrooms = useBranchClassrooms();
  const teachers = useBranchTeachers();
  const subjects = useUserSubjectNames();
  const activeClassrooms = (classrooms.data ?? []).filter((c) => c.active);
  const assignments = useClassroomsTeachers(activeClassrooms.map((c) => c.id ?? ""));
  const schedules = useBranchSchedules();
  const [chosen, setChosen] = useState("");
  // A branch admin starts on the overview of every classroom; a super-admin (no branch) picks one.
  const classroomId = chosen || (callerBranchId ? ALL : "");
  const allView = classroomId === ALL;
  // A branch admin's branch comes from the token; a super-admin has none, so the chosen classroom's branch is used.
  const branchId =
    callerBranchId ?? (classrooms.data ?? []).find((c) => c.id === classroomId)?.branchId;
  const timetable = useTimetableSetting(branchId);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [duration, setDuration] = useState(1);
  const [validFrom, setValidFrom] = useState(today);
  const [showWeekend, setShowWeekend] = useState(false);
  const [drag, setDrag] = useState<Drag>();
  const [over, setOver] = useState("");
  // The draft lives on the service (one per branch), so it survives a reload and other admins see it.
  const draftQuery = useCurrentDraft(branchId);
  const generate = useGenerateDraft(branchId ?? "");
  const moveDraft = useMoveDraftLessons(branchId ?? "");
  const deleteDraft = useDeleteDraftLessons(branchId ?? "");
  const discardDraft = useDiscardDraft(branchId ?? "");
  const commitDraft = useCommitDraft(branchId ?? "");
  const create = useCreateSchedule();
  const move = useMoveSchedules();
  const ask = useConfirm();
  const deactivate = useDeactivateSchedules();
  const setHours = useSetWeeklyHours();
  const saveSetting = useSaveTimetableSetting(branchId ?? "");

  if (classrooms.isPending || (branchId && timetable.isPending)) return <ScheduleBoardSkeleton />;
  if (timetable.isError) {
    return (
      <Card className="mt-4 p-5">
        <QueryError error={timetable.error} />
      </Card>
    );
  }
  // Without a branch (super-admin, no classroom chosen yet) no grid is drawn, so the default is never shown.
  const slots = buildSlots(timetable.data ?? DEFAULT_TIMETABLE);
  // The lunch break is one lesson hour (slot index), taken on every day and for every classroom.
  const lunchLesson = timetable.data?.lunchLesson ?? 0;
  const lunchSlot = lunchLesson >= 1 && lunchLesson <= slots.length ? lunchLesson - 1 : undefined;

  const teacherNames = new Map((teachers.data ?? []).map((t) => [t.id, fullName(t)]));
  const classroomNames = new Map((classrooms.data ?? []).map((c) => [c.id, c.name]));
  const existing: Block[] = (schedules.data ?? []).flatMap((s) => {
    const span = s.startsAt && s.endsAt ? spanOf(slots, s.startsAt, s.endsAt) : undefined;
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
  const drafts: Block[] = draftToBlocks(draftQuery.data?.lessons, slots);
  const draftCount = draftQuery.data?.lessons?.length ?? 0;
  // An empty draft (nothing to place) is no draft: there is nothing to save or throw away.
  const hasDraft = draftCount > 0 || (draftQuery.data?.missing?.length ?? 0) > 0;
  const unplaced = (draftQuery.data?.missing ?? []).map(
    (m) =>
      `${classroomNames.get(m.classroomId)}: ${subjects.data?.get(m.subjectId ?? "") ?? "Ders"} · ${m.missingHours} saat`,
  );
  const blocks = [...existing, ...drafts];
  const conflicts = [
    ...findConflicts(blocks),
    ...(lunchSlot === undefined ? [] : findLunchConflicts(blocks, lunchSlot)),
  ];
  const conflictIds = new Set(conflicts.flatMap((c) => [c.a.id, c.b.id]));
  // Back-to-back lessons of one teacher/subject/classroom are drawn as a single wider box.
  const groups = mergeAdjacent(blocks.map((b) => ({ ...b, draft: !b.schedule })));
  const conflicted = (g: { ids: string[] }) => g.ids.some((id) => conflictIds.has(id));
  const mine = blocks.filter((b) => b.classroomId === classroomId);
  const hidden =
    (schedules.data ?? []).filter((s) => s.classroomId === classroomId).length -
    existing.filter((b) => b.classroomId === classroomId).length;
  const weekend =
    showWeekend ||
    (allView ? blocks : mine).some((b) => b.dayOfWeek === "SATURDAY" || b.dayOfWeek === "SUNDAY");
  const days = weekend ? WEEKDAYS : WEEKDAYS.slice(0, 5);
  const classroomAssignments = assignments.byClassroom.get(classroomId) ?? [];
  const branchClassrooms = activeClassrooms.filter((c) => c.branchId === branchId);

  // Assignments that still need hours, across the branch: the cards of the overview.
  const pendingCards = branchClassrooms.flatMap((c) =>
    (assignments.byClassroom.get(c.id ?? "") ?? []).flatMap((a) => {
      const required = a.weeklyHours ?? 0;
      const placed = hoursOf(blocks, c.id ?? "", a.teacherId, a.subjectId);
      return required > placed
        ? [{ key: a.assignmentId ?? "", classroom: c, a, left: required - placed }]
        : [];
    }),
  );

  function placeLunch(slot: number) {
    if (timetable.data) saveSetting.mutate({ ...timetable.data, lunchLesson: slot + 1 });
  }

  async function removeLunch() {
    if (!timetable.data) return;
    const ok = await ask({
      title: "Öğle arası kaldırılsın mı?",
      text: "Bu saat tüm günlerde ve sınıflarda yeniden boşalır.",
      confirmLabel: "Kaldır",
      danger: true,
    });
    if (ok) saveSetting.mutate({ ...timetable.data, lunchLesson: 0 });
  }

  function openConflict(c: (typeof conflicts)[number]) {
    setChosen(callerBranchId ? ALL : c.a.classroomId);
  }

  async function removeGroup(g: { ids: string[]; draft: boolean }) {
    if (g.draft) {
      deleteDraft.mutate(g.ids);
      return;
    }
    const many = g.ids.length > 1;
    const ok = await ask({
      title: many ? "Bu ders bloğu kaldırılsın mı?" : "Ders programdan kaldırılsın mı?",
      text: many
        ? `Bloktaki ${g.ids.length} program pasife alınır. Oluşmuş dersler kalır.`
        : "Oluşmuş dersler kalır.",
      confirmLabel: "Kaldır",
      danger: true,
    });
    if (ok) deactivate.mutate(g.ids);
  }

  // What dropping the current drag on (day, slot) would do: the hours it covers, and why it cannot (if so).
  function dropPlan(day: Weekday, slot: number) {
    if (!drag) return undefined;
    // The lunch break takes just the hour under the pointer, whatever the day.
    if (drag.kind === "lunch") {
      return {
        lunch: true,
        dragged: undefined,
        targetClassroom: "",
        span: { first: slot, last: slot },
        error: undefined,
      };
    }
    const dragged = drag.kind === "move" ? groups.find((g) => g.id === drag.id) : undefined;
    const teacherId = drag.kind === "new" ? drag.teacherId : dragged?.teacherId;
    const targetClassroom = drag.kind === "new" ? drag.classroomId : dragged?.classroomId;
    if (!teacherId || !targetClassroom || (drag.kind === "move" && !dragged)) return undefined;
    const span: SlotSpan = {
      first: slot,
      last: slot + (dragged ? sizeOf(dragged.span) : duration) - 1,
    };
    const busy = (own: (b: Block) => boolean) =>
      overlaps(blocks.filter(own), day, span, dragged?.ids);
    let error: string | undefined;
    if (span.last >= slots.length) error = "Ders, programın saat aralığına sığmıyor.";
    else if (lunchSlot !== undefined && span.first <= lunchSlot && lunchSlot <= span.last)
      error = "Bu saat öğle arası.";
    else if (busy((b) => b.classroomId === targetClassroom))
      error = "Bu saatlerde sınıfın başka bir dersi var.";
    else if (busy((b) => b.teacherId === teacherId))
      error = "Öğretmen bu saatlerde başka bir sınıfta ders veriyor.";
    return { lunch: false, dragged, targetClassroom, span, error };
  }

  // Landing preview under the pointer: the hours the lesson would take, tinted red when it cannot go there.
  const overAt = over.lastIndexOf("-");
  const overPlan = over
    ? dropPlan(over.slice(0, overAt) as Weekday, Number(over.slice(overAt + 1)))
    : undefined;
  const previewTone = (day: string, slot: number) =>
    overPlan &&
    (overPlan.lunch || day === over.slice(0, overAt)) &&
    slot >= overPlan.span.first &&
    slot <= Math.min(overPlan.span.last, slots.length - 1)
      ? overPlan.error
        ? "bg-(--status-danger-bg)"
        : "bg-brand-tint"
      : undefined;
  const draggedId = drag?.kind === "move" ? drag.id : undefined;
  const isCardDragged = (classroom: string, teacher?: string, subject?: string) =>
    drag?.kind === "new" &&
    drag.classroomId === classroom &&
    drag.teacherId === teacher &&
    drag.subjectId === subject;

  function drop(day: Weekday, slot: number) {
    setOver("");
    const plan = dropPlan(day, slot);
    if (!plan || !drag || !branchId) return;
    if (plan.error) {
      toast.error(plan.error);
      return;
    }
    if (plan.lunch) {
      placeLunch(slot);
      return;
    }
    const { dragged, targetClassroom, span } = plan;
    const times = timesOf(slots, span);
    if (dragged) {
      if (dragged.dayOfWeek === day && dragged.span.first === span.first) return;
      // A merged box moves all of its lessons together, keeping their order.
      const delta = span.first - dragged.span.first;
      const shift = (b: Block): SlotSpan => ({
        first: b.span.first + delta,
        last: b.span.last + delta,
      });
      if (dragged.draft) {
        // The service refuses a lesson that lands on a sibling still in its old place: the ones heading
        // the way of the move go first (last first when moving later, first first when moving earlier).
        const members = drafts
          .filter((d) => dragged.ids.includes(d.id))
          .sort((a, b) => (delta < 0 ? a.span.first - b.span.first : b.span.first - a.span.first));
        moveDraft.mutate(
          members.map((m) => ({
            lessonId: m.id,
            body: { dayOfWeek: day, ...timesOf(slots, shift(m)) },
          })),
        );
        return;
      }
      move.mutate(
        blocks.flatMap((m) =>
          m.schedule && dragged.ids.includes(m.id)
            ? [
                {
                  scheduleId: m.id,
                  body: {
                    teacherId: m.teacherId,
                    subjectId: m.subjectId,
                    dayOfWeek: day,
                    ...timesOf(slots, shift(m)),
                    validFrom: m.schedule.validFrom ?? validFrom,
                    validTo: m.schedule.validTo,
                  },
                },
              ]
            : [],
        ),
      );
    } else if (drag.kind === "new") {
      create.mutate({
        body: toCreateSchedule(
          {
            classroomId: targetClassroom,
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

  // The service distributes what every classroom of the branch still needs around the lessons that
  // already exist and stores the result as the branch's draft.
  async function autoPlan() {
    if (!branchId) return;
    if (
      hasDraft &&
      !(await ask({
        title: "Taslak yeniden dağıtılsın mı?",
        text: "Mevcut taslak silinir, yerine yenisi hazırlanır.",
        confirmLabel: "Yeniden dağıt",
        danger: true,
      }))
    ) {
      return;
    }
    generate.mutate(
      days.map((d) => d.value),
      {
        onSuccess: (draft) => {
          if (draft.lessons?.length || draft.missing?.length) toast.success("Taslak hazırlandı.");
          else
            toast.info(
              "Yerleştirilecek ders yok. Sınıf seçip kartlardaki haftalık saatleri girin.",
            );
        },
      },
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
    // A lifted, slightly tilted copy follows the pointer, as if the lesson were being carried.
    const el = e.currentTarget as HTMLElement;
    const ghost = el.cloneNode(true) as HTMLElement;
    Object.assign(ghost.style, {
      position: "fixed",
      top: "-1000px",
      left: "-1000px",
      width: `${el.offsetWidth}px`,
      margin: "0",
      transform: "rotate(-3deg) scale(1.06)",
      outline: "2px solid #6D4AFF",
      outlineOffset: "-1px",
      opacity: "0.95",
      pointerEvents: "none",
    });
    document.body.appendChild(ghost);
    e.dataTransfer.setDragImage(ghost, e.nativeEvent.offsetX, e.nativeEvent.offsetY);
    setTimeout(() => ghost.remove(), 0);
    setTimeout(() => setDrag(next), 0);
  };
  const endDrag = () => {
    setDrag(undefined);
    setOver("");
  };

  // The fixed "Öğle arası" card; once it is on the board, the box itself is dragged instead.
  const lunchCard =
    lunchSlot === undefined ? (
      <div
        draggable
        onDragStart={startDrag({ kind: "lunch" })}
        onDragEnd={endDrag}
        className={cn(
          "flex cursor-grab items-center gap-1 rounded-full border border-line bg-sub px-3 py-1 text-[13px] transition-opacity active:cursor-grabbing",
          drag?.kind === "lunch" && "opacity-40",
        )}
      >
        <Utensils size={14} strokeWidth={1.5} aria-hidden="true" />
        <span className="font-medium">Öğle arası</span>
      </div>
    ) : null;

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
            onChange={setChosen}
            options={[
              ...(callerBranchId ? [{ value: ALL, label: "Tüm sınıflar" }] : []),
              ...activeClassrooms.map((c) => ({ value: c.id ?? "", label: c.name ?? "" })),
            ]}
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
        {/* A div, not a label: a click inside the calendar would re-click the trigger through a label. */}
        <div className="flex flex-col gap-1 text-[13px] text-dim">
          <span>Geçerli (başlangıç)</span>
          <DatePicker
            aria-label="Geçerli (başlangıç)"
            className="w-52"
            value={validFrom}
            onChange={setValidFrom}
          />
        </div>
        <SegButton active={showWeekend} onClick={() => setShowWeekend(!showWeekend)}>
          Hafta sonu
        </SegButton>
        <PillButton
          disabled={!branchId || generate.isPending || assignments.pending || schedules.isPending}
          onClick={autoPlan}
        >
          <Wand2 size={14} strokeWidth={1.75} aria-hidden="true" />
          {generate.isPending ? "Dağıtılıyor…" : hasDraft ? "Yeniden dağıt" : "Otomatik dağıt"}
        </PillButton>
        <PillButton
          disabled={!branchId}
          aria-expanded={settingsOpen}
          onClick={() => setSettingsOpen(!settingsOpen)}
        >
          <Clock size={14} strokeWidth={1.75} aria-hidden="true" />
          Ders saatleri
        </PillButton>
      </div>
      {settingsOpen && branchId && timetable.data && (
        <TimetableSettingForm
          branchId={branchId}
          setting={timetable.data}
          hasDrafts={hasDraft}
          onSaved={() => hasDraft && discardDraft.mutate()}
          onDone={() => setSettingsOpen(false)}
        />
      )}

      {hasDraft && (
        <div className="mt-4 flex flex-wrap items-center gap-3 rounded-lg border border-dashed border-line bg-sub px-4 py-3 text-[13px]">
          <span>
            <span className="font-medium">Taslak:</span> {draftCount} ders yerleştirildi. Kaydedene
            kadar programa işlenmez; sürükleyerek düzeltebilirsiniz.
          </span>
          <span className="ml-auto flex gap-2">
            <PillButton
              disabled={commitDraft.isPending || draftCount === 0}
              onClick={() => commitDraft.mutate(validFrom)}
            >
              {commitDraft.isPending ? "Kaydediliyor…" : "Taslağı kaydet"}
            </PillButton>
            <PillButton disabled={commitDraft.isPending} onClick={() => discardDraft.mutate()}>
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

      {conflicts.length > 0 && (
        <div
          role="status"
          className="mt-3 rounded-lg border border-(--status-danger-fg) px-4 py-3 text-[13px]"
        >
          <div className="font-medium text-(--status-danger-fg)">
            {conflicts.length} çakışma var
          </div>
          <ul className="mt-1 flex flex-col gap-1">
            {conflicts.slice(0, 8).map((c) => {
              const hours = `${slots[c.span.first].startsAt}–${slots[c.span.last].endsAt}`;
              const what =
                c.kind === "lunch"
                  ? `${classroomNames.get(c.a.classroomId)}: ${subjects.data?.get(c.a.subjectId) ?? "Ders"} öğle arasına denk geliyor`
                  : c.kind === "teacher"
                    ? `${teacherNames.get(c.a.teacherId) ?? "Öğretmen"}: ${classroomNames.get(c.a.classroomId)} ve ${classroomNames.get(c.b.classroomId)} sınıfında aynı anda`
                    : `${classroomNames.get(c.a.classroomId)}: ${subjects.data?.get(c.a.subjectId) ?? "Ders"} ve ${subjects.data?.get(c.b.subjectId) ?? "Ders"} aynı anda`;
              return (
                <li key={`${c.kind}-${c.a.id}-${c.b.id}`}>
                  <button
                    type="button"
                    onClick={() => openConflict(c)}
                    className="text-left underline-offset-2 hover:underline"
                  >
                    {DAY_LABELS[c.dayOfWeek as Weekday]} {hours} · {what}
                  </button>
                </li>
              );
            })}
          </ul>
          {conflicts.length > 8 && (
            <div className="mt-1 text-dim">ve {conflicts.length - 8} çakışma daha</div>
          )}
        </div>
      )}

      {!classroomId ? (
        <p className="mt-6 text-sm text-dim">
          Programı görmek veya haftalık ders saatlerini girmek için bir sınıf seçin.
        </p>
      ) : schedules.isError ? (
        <QueryError error={schedules.error} />
      ) : allView ? (
        <>
          <div className="mt-4 flex flex-wrap items-center gap-2">
            <span className="text-[13px] text-dim">
              Yerleştirilecek dersler, tabloya sürükleyip bırakın:
            </span>
            {lunchCard}
            {assignments.pending ? (
              <Skeleton className="h-8 w-40" />
            ) : pendingCards.length === 0 ? (
              <span className="text-[13px] text-dim">
                Yerleştirilecek ders yok. Haftalık ders saatlerini sınıf görünümünden
                girebilirsiniz.
              </span>
            ) : (
              pendingCards.map(({ key, classroom, a, left }) => (
                <div
                  key={key}
                  draggable
                  onDragStart={startDrag({
                    kind: "new",
                    classroomId: classroom.id ?? "",
                    teacherId: a.teacherId ?? "",
                    subjectId: a.subjectId ?? "",
                  })}
                  onDragEnd={endDrag}
                  className={cn(
                    "flex cursor-grab items-center gap-1 rounded-full border border-line bg-brand-tint px-3 py-1 text-[13px] transition-opacity active:cursor-grabbing",
                    isCardDragged(classroom.id ?? "", a.teacherId, a.subjectId) && "opacity-40",
                  )}
                >
                  <GripVertical size={14} strokeWidth={1.5} aria-hidden="true" />
                  <span className="font-medium">{a.subjectName}</span>
                  <span className="text-dim">
                    {a.firstName} {a.lastName}
                  </span>
                  <span className="rounded-full bg-white/80 px-2 text-[12px] font-medium">
                    {classroom.name}
                  </span>
                  <span className="text-dim">kalan {left} saat</span>
                </div>
              ))
            )}
          </div>
          <WeekOverviewGrid
            slots={slots}
            lunchSlot={lunchSlot}
            lunchDragged={drag?.kind === "lunch"}
            onLunchDragStart={startDrag({ kind: "lunch" })}
            onLunchRemove={removeLunch}
            days={days}
            blocks={groups}
            classroomNames={classroomNames}
            teacherNames={teacherNames}
            subjectNames={subjects.data}
            conflictIds={conflictIds}
            dragging={!!drag}
            tone={previewTone}
            draggedId={draggedId}
            onOver={setOver}
            onDrop={drop}
            onBlockDragStart={(id) => startDrag({ kind: "move", id })}
            onDragEnd={endDrag}
            onRemove={removeGroup}
            onOpen={setChosen}
          />
        </>
      ) : (
        <>
          <div className="mt-4 flex flex-wrap items-center gap-2">
            <span className="text-[13px] text-dim">
              Haftalık ders saatini girin, kartı programa sürükleyin:
            </span>
            {lunchCard}
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
                        classroomId,
                        teacherId: a.teacherId ?? "",
                        subjectId: a.subjectId ?? "",
                      })}
                      onDragEnd={endDrag}
                      className={cn(
                        "flex cursor-grab items-center gap-1 rounded-full bg-brand-tint px-3 py-1 text-[13px] transition-opacity active:cursor-grabbing",
                        isCardDragged(classroomId, a.teacherId, a.subjectId) && "opacity-40",
                      )}
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
                gridTemplateRows: `auto repeat(${slots.length}, minmax(56px, auto))`,
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
              {slots.map((slot, i) => (
                <div
                  key={slot.startsAt}
                  style={{ gridRow: i + 2, gridColumn: 1 }}
                  className="border-b border-line px-2 py-2 text-[12px] font-medium"
                >
                  {slot.startsAt}-{slot.endsAt}
                </div>
              ))}
              {days.map((d, col) =>
                slots.map((_, i) => {
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
                        "border-b border-l border-line transition-colors",
                        previewTone(d.value, i),
                      )}
                    />
                  );
                }),
              )}
              {groups
                .filter((g) => g.classroomId === classroomId)
                .map((b) => {
                  const col = days.findIndex((d) => d.value === b.dayOfWeek);
                  if (col === -1) return null;
                  const draft = b.draft;
                  return (
                    <div
                      key={b.id}
                      title={conflicted(b) ? "Çakışma var" : undefined}
                      draggable
                      onDragStart={startDrag({ kind: "move", id: b.id })}
                      onDragEnd={endDrag}
                      style={{
                        gridColumn: col + 2,
                        gridRow: `${b.span.first + 2} / span ${b.span.last - b.span.first + 1}`,
                      }}
                      className={cn(
                        "anim-fade relative m-0.5 cursor-grab rounded-lg border p-2 pr-6 text-center text-[13px] transition-opacity active:cursor-grabbing",
                        draggedId === b.id && "opacity-40",
                        draft
                          ? "border-dashed border-ink/40 bg-white"
                          : "border-line bg-brand-tint",
                        conflicted(b) && "border-(--status-danger-fg) bg-(--status-danger-bg)",
                        drag && "pointer-events-none",
                      )}
                    >
                      <div className="font-medium">{subjects.data?.get(b.subjectId) ?? "Ders"}</div>
                      <div className="text-dim">{teacherNames.get(b.teacherId) ?? "Öğretmen"}</div>
                      <button
                        type="button"
                        aria-label={draft ? "Taslak dersi kaldır" : "Dersi programdan kaldır"}
                        disabled={deactivate.isPending}
                        onClick={() => removeGroup(b)}
                        className="absolute top-1 right-1 grid size-5 place-items-center rounded-full hover:bg-sub"
                      >
                        <X size={12} strokeWidth={1.75} aria-hidden="true" />
                      </button>
                    </div>
                  );
                })}
              {lunchSlot !== undefined &&
                days.map((d, col) => (
                  <div
                    key={`lunch-${d.value}`}
                    draggable
                    onDragStart={startDrag({ kind: "lunch" })}
                    onDragEnd={endDrag}
                    style={{ gridColumn: col + 2, gridRow: lunchSlot + 2 }}
                    className={cn(
                      "anim-fade relative m-0.5 flex cursor-grab items-center justify-center gap-1 rounded-lg border border-line bg-sub p-2 text-[13px] font-medium text-dim transition-opacity active:cursor-grabbing",
                      drag && "pointer-events-none",
                      drag?.kind === "lunch" && "opacity-40",
                    )}
                  >
                    <Utensils size={14} strokeWidth={1.5} aria-hidden="true" />
                    Öğle arası
                    <button
                      type="button"
                      aria-label="Öğle arasını kaldır"
                      onClick={removeLunch}
                      className="absolute top-1 right-1 grid size-5 place-items-center rounded-full hover:bg-white"
                    >
                      <X size={12} strokeWidth={1.75} aria-hidden="true" />
                    </button>
                  </div>
                ))}
            </div>
          </div>
          {hidden > 0 && (
            <p className="mt-3 text-[13px] text-dim">
              {hidden} program {slots[0].startsAt}–{slots[slots.length - 1].endsAt} aralığı dışında
              olduğu için burada görünmüyor; Liste görünümünden düzenleyin.
            </p>
          )}
        </>
      )}
    </Card>
  );
}
