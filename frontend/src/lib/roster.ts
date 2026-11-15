import type { AttendanceStatus } from "./status/attendance";

export const ATTENDANCE_CHOICES: readonly AttendanceStatus[] = [
  "PRESENT",
  "ABSENT",
  "LATE",
  "EXCUSED",
];

// studentId -> chosen status. A student without a key is unmarked: the server never defaults and neither does the UI.
export type Marks = Record<string, AttendanceStatus>;

export function markedCount(studentIds: string[], marks: Marks): number {
  return studentIds.filter((id) => marks[id] !== undefined).length;
}

export function allMarked(studentIds: string[], marks: Marks): boolean {
  return studentIds.length > 0 && markedCount(studentIds, marks) === studentIds.length;
}

export function markAll(studentIds: string[], status: AttendanceStatus): Marks {
  return Object.fromEntries(studentIds.map((id) => [id, status]));
}

export function toEntries(studentIds: string[], marks: Marks) {
  return studentIds.map((studentId) => ({ studentId, status: marks[studentId] }));
}
