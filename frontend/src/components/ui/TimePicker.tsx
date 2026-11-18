import { cn } from "@/lib/utils";
import { Select, type SelectOption } from "./Select";

const pad = (n: number) => String(n).padStart(2, "0");
const HOURS: SelectOption[] = Array.from({ length: 24 }, (_, h) => ({
  value: pad(h),
  label: pad(h),
}));
const STEP = 5;

export interface TimePickerProps {
  id?: string;
  /** "HH:mm", or "" for no time. */
  value: string;
  onChange: (value: string) => void;
  disabled?: boolean;
  invalid?: boolean;
  "aria-label"?: string;
  className?: string;
}

// Time field in the app's own look: an hour and a minute dropdown (5-minute steps, plus the current
// minute when it is off the step) instead of the browser's clock.
export function TimePicker({
  id,
  value,
  onChange,
  disabled = false,
  invalid = false,
  "aria-label": ariaLabel = "Saat",
  className,
}: TimePickerProps) {
  const [hour = "", minute = ""] = value ? value.slice(0, 5).split(":") : [];
  const minutes: SelectOption[] = Array.from({ length: 60 / STEP }, (_, i) => pad(i * STEP)).map(
    (m) => ({ value: m, label: m }),
  );
  if (minute && !minutes.some((m) => m.value === minute)) {
    minutes.push({ value: minute, label: minute });
    minutes.sort((a, b) => a.value.localeCompare(b.value));
  }

  return (
    <div className={cn("flex items-center gap-2", className)}>
      <Select
        id={id}
        aria-label={`${ariaLabel}: saat`}
        className="w-full"
        placeholder="SS"
        disabled={disabled}
        invalid={invalid}
        value={hour}
        onChange={(h) => onChange(`${h}:${minute || "00"}`)}
        options={HOURS}
      />
      <span className="text-dim" aria-hidden="true">
        :
      </span>
      <Select
        aria-label={`${ariaLabel}: dakika`}
        className="w-full"
        placeholder="DD"
        disabled={disabled}
        invalid={invalid}
        value={minute}
        onChange={(m) => onChange(`${hour || "00"}:${m}`)}
        options={minutes}
      />
    </div>
  );
}
