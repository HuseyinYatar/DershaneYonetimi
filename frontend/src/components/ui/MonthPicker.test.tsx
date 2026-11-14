import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { MonthPicker } from "./MonthPicker";

afterEach(cleanup);

describe("MonthPicker", () => {
  it("shows the selected month on the trigger", () => {
    render(<MonthPicker value="2026-11" onChange={() => {}} />);
    expect(screen.getByRole("button", { name: /Kasım 2026/ })).toBeTruthy();
  });

  it("picks a month in the browsed year and closes", () => {
    const onChange = vi.fn();
    render(<MonthPicker value="2026-11" onChange={onChange} />);
    fireEvent.click(screen.getByRole("button", { name: /Kasım 2026/ }));
    fireEvent.click(screen.getByRole("button", { name: "Önceki yıl" }));
    fireEvent.click(screen.getByRole("button", { name: "Mar" }));
    expect(onChange).toHaveBeenCalledWith("2025-03");
    expect(screen.queryByRole("dialog")).toBeNull();
  });

  it("closes on Escape", () => {
    render(<MonthPicker value={undefined} onChange={() => {}} />);
    fireEvent.click(screen.getByRole("button", { name: /Ay/ }));
    expect(screen.getByRole("dialog")).toBeTruthy();
    fireEvent.keyDown(document, { key: "Escape" });
    expect(screen.queryByRole("dialog")).toBeNull();
  });
});
