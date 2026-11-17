import { type Control, type FieldValues, type Path, useController } from "react-hook-form";
import { Select, type SelectProps } from "./Select";

// Select wired to a react-hook-form field (the value is always the option's string value).
export function FormSelect<T extends FieldValues>({
  control,
  name,
  onValueChange,
  ...props
}: Omit<SelectProps, "value" | "onChange" | "invalid"> & {
  control: Control<T>;
  name: Path<T>;
  onValueChange?: (value: string) => void;
}) {
  const { field, fieldState } = useController({ control, name });
  return (
    <Select
      {...props}
      value={String(field.value ?? "")}
      onChange={(value) => {
        field.onChange(value);
        onValueChange?.(value);
      }}
      invalid={!!fieldState.error}
    />
  );
}
