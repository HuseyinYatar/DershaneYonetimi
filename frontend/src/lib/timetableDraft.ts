import { ApiError } from "@/api/problem";
import { type Slot, type SlotSpan, spanOf } from "./scheduleBoard";

export interface DraftLesson {
  id?: string;
  classroomId?: string;
  teacherId?: string;
  subjectId?: string;
  dayOfWeek?: string;
  startsAt?: string;
  endsAt?: string;
}

export interface DraftBlock {
  id: string;
  classroomId: string;
  teacherId: string;
  subjectId: string;
  dayOfWeek: string;
  span: SlotSpan;
}

// The service answers a branch without a draft with this 404; for the board that is "no draft", not a failure.
export function isDraftMissing(error: unknown): boolean {
  return (
    error instanceof ApiError &&
    error.status === 404 &&
    error.problem.errorCode === "TIMETABLE_DRAFT_NOT_FOUND"
  );
}

// Draft lessons carry clock times; the board works in grid rows. A lesson that no longer fits the grid
// (the lesson hours were changed after the draft was made) has no row and is left out.
export function draftToBlocks(lessons: DraftLesson[] | undefined, slots: Slot[]): DraftBlock[] {
  return (lessons ?? []).flatMap((l) => {
    const span =
      l.startsAt && l.endsAt
        ? spanOf(slots, l.startsAt.slice(0, 5), l.endsAt.slice(0, 5))
        : undefined;
    return span && l.id && l.dayOfWeek
      ? [
          {
            id: l.id,
            classroomId: l.classroomId ?? "",
            teacherId: l.teacherId ?? "",
            subjectId: l.subjectId ?? "",
            dayOfWeek: l.dayOfWeek,
            span,
          },
        ]
      : [];
  });
}
