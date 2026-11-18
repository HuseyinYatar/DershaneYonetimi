import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { DatePicker } from "./DatePicker";
import { TimePicker } from "./TimePicker";

afterEach(cleanup);

describe("DatePicker", () => {
  it("shows the chosen date in Turkish, or the placeholder", () => {
    const { rerender } = render(<DatePicker value="" onChange={() => {}} />);
    expect(screen.getByRole("button").textContent).toContain("Tarih seç");
    rerender(<DatePicker value="2026-11-17" onChange={() => {}} />);
    expect(screen.getByRole("button").textContent).toContain("17 Kasım 2026");
  });

  it("picks a day of the shown month and closes", () => {
    const onChange = vi.fn();
    render(<DatePicker value="2026-11-17" onChange={onChange} />);
    fireEvent.click(screen.getByRole("button", { name: /17 Kasım 2026/ }));
    fireEvent.click(screen.getByRole("button", { name: "20 Kasım 2026" }));

    expect(onChange).toHaveBeenCalledWith("2026-11-20");
    expect(screen.queryByRole("dialog")).toBeNull();
  });

  it("browses months and starts weeks on Monday", () => {
    const onChange = vi.fn();
    render(<DatePicker value="2026-11-17" onChange={onChange} />);
    fireEvent.click(screen.getByRole("button", { name: /17 Kasım 2026/ }));
    fireEvent.click(screen.getByRole("button", { name: "Sonraki ay" }));
    // 1 December 2026 is a Tuesday: the grid's first row holds 30 Nov (Mon) before it.
    fireEvent.click(screen.getByRole("button", { name: "1 Aralık 2026" }));

    expect(onChange).toHaveBeenCalledWith("2026-12-01");
  });

  it("moves with the arrow keys and closes on Escape", () => {
    const onChange = vi.fn();
    render(<DatePicker value="2026-11-17" onChange={onChange} />);
    fireEvent.click(screen.getByRole("button", { name: /17 Kasım 2026/ }));
    const dialog = screen.getByRole("dialog");
    fireEvent.keyDown(dialog, { key: "ArrowRight" });
    expect(document.activeElement?.getAttribute("data-date")).toBe("2026-11-18");
    fireEvent.keyDown(dialog, { key: "Escape" });

    expect(screen.queryByRole("dialog")).toBeNull();
    expect(onChange).not.toHaveBeenCalled();
  });

  it("clears an optional date", () => {
    const onChange = vi.fn();
    render(<DatePicker value="2026-11-17" onChange={onChange} clearable />);
    fireEvent.click(screen.getByRole("button", { name: /17 Kasım 2026/ }));
    fireEvent.click(screen.getByRole("button", { name: "Temizle" }));

    expect(onChange).toHaveBeenCalledWith("");
  });
});

describe("TimePicker", () => {
  it("shows the hour and the minute", () => {
    render(<TimePicker value="08:10" onChange={() => {}} />);
    const [hour, minute] = screen.getAllByRole("combobox");
    expect(hour.textContent).toContain("08");
    expect(minute.textContent).toContain("10");
  });

  it("changes the hour and keeps the minute", () => {
    const onChange = vi.fn();
    render(<TimePicker value="08:10" onChange={onChange} />);
    fireEvent.click(screen.getAllByRole("combobox")[0]);
    fireEvent.click(screen.getByRole("option", { name: "14" }));

    expect(onChange).toHaveBeenCalledWith("14:10");
  });

  it("starts a minute of 00 when only the hour is chosen", () => {
    const onChange = vi.fn();
    render(<TimePicker value="" onChange={onChange} />);
    fireEvent.click(screen.getAllByRole("combobox")[0]);
    fireEvent.click(screen.getByRole("option", { name: "09" }));

    expect(onChange).toHaveBeenCalledWith("09:00");
  });

  it("keeps an off-step minute selectable", () => {
    render(<TimePicker value="08:07" onChange={() => {}} />);
    fireEvent.click(screen.getAllByRole("combobox")[1]);

    expect(screen.getByRole("option", { name: "07" })).toBeTruthy();
  });
});
