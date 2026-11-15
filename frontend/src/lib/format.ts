// All user-facing numbers and dates go through here (tr-TR: "72,5", "%94", "Cuma 13 Kasım").
const LOCALE = "tr-TR";

const decimal1 = new Intl.NumberFormat(LOCALE, {
  minimumFractionDigits: 1,
  maximumFractionDigits: 1,
});
const decimal2 = new Intl.NumberFormat(LOCALE, { maximumFractionDigits: 2 });
const integer = new Intl.NumberFormat(LOCALE, { maximumFractionDigits: 0 });
const weekday = new Intl.DateTimeFormat(LOCALE, { weekday: "long" });
const dayMonth = new Intl.DateTimeFormat(LOCALE, { day: "numeric", month: "long" });
const dayShortMonth = new Intl.DateTimeFormat(LOCALE, { day: "numeric", month: "short" });
const fullDate = new Intl.DateTimeFormat(LOCALE, {
  day: "numeric",
  month: "long",
  year: "numeric",
});
const time = new Intl.DateTimeFormat(LOCALE, { hour: "2-digit", minute: "2-digit", hour12: false });
const monthName = new Intl.DateTimeFormat(LOCALE, { month: "short" });
const monthYear = new Intl.DateTimeFormat(LOCALE, { month: "long", year: "numeric" });

// The exam API sends decimals as strings ("72.50"); non-numeric input renders as an em dash.
function toNumber(value: number | string | null | undefined): number | null {
  if (value === null || value === undefined || value === "") return null;
  const n = typeof value === "number" ? value : Number(value);
  return Number.isFinite(n) ? n : null;
}

export function formatNet(value: number | string | null | undefined): string {
  const n = toNumber(value);
  return n === null ? "—" : decimal1.format(n);
}

export function formatInt(value: number | string | null | undefined): string {
  const n = toNumber(value);
  return n === null ? "—" : integer.format(n);
}

// `value` is already a percentage (94 -> "%94").
export function formatPercent(value: number | string | null | undefined): string {
  const n = toNumber(value);
  return n === null ? "—" : `%${integer.format(n)}`;
}

// The API sends success rates as ratios ("0.3800").
export function ratioToPercent(ratio: number | string | null | undefined): number {
  return Math.round((toNumber(ratio) ?? 0) * 100);
}

const cap = (text: string) => text.charAt(0).toLocaleUpperCase(LOCALE) + text.slice(1);

// "Cuma 13 Kasım"
export function formatDateLong(date: Date): string {
  return `${cap(weekday.format(date))} ${dayMonth.format(date)}`;
}

// "8 Kasım 2026"
export function formatFullDate(date: Date): string {
  return fullDate.format(date);
}

// "8 Kasım 2026 Pazar"
export function formatFullDateWithWeekday(date: Date): string {
  return `${fullDate.format(date)} ${cap(weekday.format(date))}`;
}

// "12 Kas"
export function formatDayMonth(date: Date): string {
  return dayShortMonth.format(date);
}

// "12 Kas, 23:59"
export function formatDeadline(date: Date): string {
  return `${dayShortMonth.format(date)}, ${time.format(date)}`;
}

export function formatTime(date: Date): string {
  return time.format(date);
}

// "Kas" (short month, for chart axes)
export function formatMonthShort(date: Date): string {
  return cap(monthName.format(date).replace(".", ""));
}

// "Kasım 2026"
export function formatMonthYear(date: Date): string {
  return cap(monthYear.format(date));
}

// "HH:mm:ss" or "HH:mm" local time strings from the API -> "09:00"
export function formatClock(value: string | undefined): string {
  return value ? value.slice(0, 5) : "—";
}

// Date-only API values ("2026-11-08") must not be parsed as UTC midnight.
export function parseDateOnly(value: string): Date {
  const [y, m, d] = value.split("-").map(Number);
  return new Date(y, m - 1, d);
}

export function greetingFor(hour: number): string {
  if (hour >= 5 && hour < 12) return "Günaydın";
  if (hour >= 12 && hour < 18) return "İyi günler";
  return "İyi akşamlar";
}

export function initialsOf(firstName?: string, lastName?: string): string {
  const letters = [firstName, lastName].map((part) => part?.trim().charAt(0) ?? "").join("");
  return letters.toLocaleUpperCase(LOCALE) || "?";
}

// "8 Kasım"
export function formatDayMonthLong(date: Date): string {
  return dayMonth.format(date);
}

const MS_PER_DAY = 86_400_000;

// "Bugün · 23:59", "Yarın · 23:59", "Salı, 17 Kasım · 18:00" (calendar days, local time).
export function formatRelativeDeadline(due: Date, now: Date): string {
  const dayStart = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
  const diff = Math.round((dayStart(due) - dayStart(now)) / MS_PER_DAY);
  const when = diff === 0 ? "Bugün" : diff === 1 ? "Yarın" : formatDateLong(due);
  return `${when} · ${time.format(due)}`;
}

// Net difference between exams: up to two decimals ("4,25").
export function formatDelta(value: number): string {
  return decimal2.format(value);
}
