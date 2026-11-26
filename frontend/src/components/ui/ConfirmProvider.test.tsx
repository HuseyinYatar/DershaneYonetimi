import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { useConfirm } from "@/lib/useConfirm";
import { ConfirmProvider } from "./ConfirmProvider";

afterEach(cleanup);

function Harness({ onResult }: { onResult: (ok: boolean) => void }) {
  const ask = useConfirm();
  return (
    <button
      type="button"
      onClick={async () =>
        onResult(
          await ask({
            title: "Atama kaldırılsın mı?",
            text: "Bu işlem geri alınamaz.",
            confirmLabel: "Kaldır",
            danger: true,
          }),
        )
      }
    >
      aç
    </button>
  );
}

function open(onResult = vi.fn()) {
  render(
    <ConfirmProvider>
      <Harness onResult={onResult} />
    </ConfirmProvider>,
  );
  fireEvent.click(screen.getByRole("button", { name: "aç" }));
  return onResult;
}

describe("ConfirmProvider", () => {
  it("shows the question and puts the focus on the safe choice", () => {
    open();

    expect(screen.getByRole("alertdialog", { name: "Atama kaldırılsın mı?" })).toBeTruthy();
    expect(screen.getByText("Bu işlem geri alınamaz.")).toBeTruthy();
    expect(document.activeElement).toBe(screen.getByRole("button", { name: "Vazgeç" }));
  });

  it("resolves true on confirm and closes", async () => {
    const onResult = open();
    fireEvent.click(screen.getByRole("button", { name: "Kaldır" }));

    await waitFor(() => expect(onResult).toHaveBeenCalledWith(true));
    expect(screen.queryByRole("alertdialog")).toBeNull();
  });

  it("resolves false on cancel, Escape and a click outside", async () => {
    const onResult = open();
    fireEvent.click(screen.getByRole("button", { name: "Vazgeç" }));
    await waitFor(() => expect(onResult).toHaveBeenLastCalledWith(false));

    fireEvent.click(screen.getByRole("button", { name: "aç" }));
    fireEvent.keyDown(screen.getByRole("alertdialog"), { key: "Escape" });
    await waitFor(() => expect(onResult).toHaveBeenCalledTimes(2));

    fireEvent.click(screen.getByRole("button", { name: "aç" }));
    const backdrop = screen.getByRole("alertdialog").parentElement as HTMLElement;
    fireEvent.mouseDown(backdrop);
    await waitFor(() => expect(onResult).toHaveBeenCalledTimes(3));
    expect(onResult.mock.calls.every(([ok]) => ok === false)).toBe(true);
  });
});
