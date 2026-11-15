import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { StatusBadge, type StatusTone } from "./StatusBadge";

afterEach(cleanup);

const TONES: StatusTone[] = ["success", "danger", "info", "warning", "neutral"];

describe("StatusBadge", () => {
  it.each(TONES)("renders the label as text for tone %s", (tone) => {
    render(<StatusBadge tone={tone} label="Etiket" />);
    expect(screen.getByText("Etiket")).toBeTruthy();
  });

  it("hides the icon from assistive technology", () => {
    const { container } = render(<StatusBadge tone="info" label="Var" />);
    expect(container.querySelector("svg")?.getAttribute("aria-hidden")).toBe("true");
    const { container: solid } = render(<StatusBadge tone="success" label="Var" />);
    expect(solid.querySelector("[aria-hidden=true] svg")).not.toBeNull();
  });

  it.each(TONES)("icon-only variant exposes the label as accessible name for tone %s", (tone) => {
    const { container } = render(<StatusBadge tone={tone} label="Gecikti" iconOnly />);
    const el = screen.getByRole("img", { name: "Gecikti" });
    expect(el.getAttribute("title")).toBe("Gecikti");
    expect(container.textContent).toBe("");
  });
});
