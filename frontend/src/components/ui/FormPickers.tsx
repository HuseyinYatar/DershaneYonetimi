import { type Control, type FieldValues, type Path, useController } from "react-hook-form";
import { DatePicker, type DatePickerProps } from "./DatePicker";
import { TimePicker, type TimePickerProps } from "./TimePicker";

// DatePicker / TimePicker wired to a react-hook-form field (the value is the "YYYY-MM-DD" / "HH:mm" string).
export function FormDatePicker<T extends FieldValues>({
  control,
  name,
  ...props
}: Omit<DatePickerProps, "value" | "onChange" | "invalid"> & {
  control: Control<T>;
  name: Path<T>;
}) {
  const { field, fieldState } = useController({ control, name });
  return (
    <DatePicker
      {...props}
      value={String(field.value ?? "")}
      onChange={field.onChange}
      invalid={!!fieldState.error}
    />
  );
}

export function FormTimePicker<T extends FieldValues>({
  control,
  name,
  ...props
}: Omit<TimePickerProps, "value" | "onChange" | "invalid"> & {
  control: Control<T>;
  name: Path<T>;
}) {
  const { field, fieldState } = useController({ control, name });
  return (
    <TimePicker
      {...props}
      value={String(field.value ?? "")}
      onChange={field.onChange}
      invalid={!!fieldState.error}
    />
  );
}
