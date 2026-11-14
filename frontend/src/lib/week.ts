// Monday-start week math in local time.
const dayNames = new Intl.DateTimeFormat("tr-TR", { weekday: "short" });
const dayMonth = new Intl.DateTimeFormat("tr-TR", { day: "numeric", month: "long" });
const dayShortMonth = new Intl.DateTimeFormat("tr-TR", { day: "numeric", month: "short" });

export function startOfWeek(date: Date): Date {
  const offset = (date.getDay() + 6) % 7; // Monday = 0
  return new Date(date.getFullYear(), date.getMonth(), date.getDate() - offset);
}

export function weekDays(date: Date): Date[] {
  const start = startOfWeek(date);
  return Array.from(
    { length: 7 },
    (_, i) => new Date(start.getFullYear(), start.getMonth(), start.getDate() + i),
  );
}

// Local "YYYY-MM-DD", used as filter value in the URL and to bucket deadlines by day.
export function dateKey(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

export function shortDayName(date: Date): string {
  return dayNames.format(date).replace(".", "");
}

// "9–15 Kasım" or, across a month boundary, "30 Eki – 5 Kas".
export function weekRangeLabel(days: Date[]): string {
  const first = days[0];
  const last = days[days.length - 1];
  if (first.getMonth() === last.getMonth()) {
    return `${first.getDate()}–${dayMonth.format(last)}`;
  }
  return `${dayShortMonth.format(first)} – ${dayShortMonth.format(last)}`;
}
