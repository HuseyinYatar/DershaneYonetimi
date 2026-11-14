import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { StatusFilter } from "./StatusFilter";

afterEach(cleanup);

const STATUSES = ["ASSIGNED", "OVERDUE"] as const;

function setup(value: "ASSIGNED" | "OVERDUE" | undefined = undefined) {
  const onChange = vi.fn();
  render(<StatusFilter statuses={STATUSES} value={value} onChange={onChange} />);
  return onChange;
}

const trigger = () => screen.getByRole("button", { name: /Durum/ });

describe("StatusFilter", () => {
  it("shows the selected status on the trigger; the list is closed until clicked", () => {
    setup("OVERDUE");
    expect(trigger().textContent).toContain("Gecikti");
    expect(screen.queryByRole("group")).toBeNull();
    fireEvent.click(trigger());
    expect(screen.getByRole("group")).toBeTruthy();
  });

  it("picks a status and closes", () => {
    const onChange = setup();
    fireEvent.click(trigger());
    fireEvent.click(screen.getByRole("button", { name: /Gecikti/ }));
    expect(onChange).toHaveBeenCalledWith("OVERDUE");
    expect(screen.queryByRole("group")).toBeNull();
  });

  it("clears the filter with the all option", () => {
    const onChange = setup("ASSIGNED");
    fireEvent.click(trigger());
    fireEvent.click(screen.getByRole("button", { name: /Tümü/ }));
    expect(onChange).toHaveBeenCalledWith(undefined);
  });

  it("closes on Escape", () => {
    setup();
    fireEvent.click(trigger());
    fireEvent.keyDown(document, { key: "Escape" });
    expect(screen.queryByRole("group")).toBeNull();
  });
});
