import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { Select } from "./Select";

afterEach(cleanup);

const OPTIONS = [
  { value: "m", label: "Matematik" },
  { value: "g", label: "Geometri" },
  { value: "f", label: "Fizik" },
];

describe("Select", () => {
  it("shows the placeholder, then the chosen label", () => {
    const { rerender } = render(<Select value="" onChange={() => {}} options={OPTIONS} />);
    expect(screen.getByRole("combobox").textContent).toContain("Seçin");
    rerender(<Select value="g" onChange={() => {}} options={OPTIONS} />);
    expect(screen.getByRole("combobox").textContent).toContain("Geometri");
  });

  it("picks an option with the mouse and closes", () => {
    const onChange = vi.fn();
    render(<Select value="" onChange={onChange} options={OPTIONS} />);
    fireEvent.click(screen.getByRole("combobox"));
    fireEvent.click(screen.getByRole("option", { name: "Fizik" }));
    expect(onChange).toHaveBeenCalledWith("f");
    expect(screen.queryByRole("listbox")).toBeNull();
  });

  it("picks with the keyboard", () => {
    const onChange = vi.fn();
    render(<Select value="m" onChange={onChange} options={OPTIONS} />);
    fireEvent.keyDown(screen.getByRole("combobox"), { key: "ArrowDown" });
    const list = screen.getByRole("listbox");
    fireEvent.keyDown(list, { key: "ArrowDown" });
    fireEvent.keyDown(list, { key: "Enter" });
    expect(onChange).toHaveBeenCalledWith("g");
  });

  it("closes on Escape without changing the value", () => {
    const onChange = vi.fn();
    render(<Select value="m" onChange={onChange} options={OPTIONS} />);
    fireEvent.click(screen.getByRole("combobox"));
    fireEvent.keyDown(screen.getByRole("listbox"), { key: "Escape" });
    expect(screen.queryByRole("listbox")).toBeNull();
    expect(onChange).not.toHaveBeenCalled();
  });

  it("does not open when disabled", () => {
    render(<Select value="" onChange={() => {}} options={OPTIONS} disabled />);
    fireEvent.click(screen.getByRole("combobox"));
    expect(screen.queryByRole("listbox")).toBeNull();
  });
});
