import { type ReactNode, useCallback, useEffect, useId, useRef, useState } from "react";
import { ConfirmContext, type ConfirmOptions } from "@/lib/useConfirm";
import { cn } from "@/lib/utils";
import { PRIMARY_BUTTON } from "./form";

interface Request {
  options: ConfirmOptions;
  resolve: (ok: boolean) => void;
}

// Renders the confirmation dialog for every useConfirm() caller. One at a time; a second request while
// one is open cancels the first.
export function ConfirmProvider({ children }: { children: ReactNode }) {
  const [request, setRequest] = useState<Request | null>(null);
  const requestRef = useRef<Request | null>(null);
  const cancelButton = useRef<HTMLButtonElement>(null);
  const confirmButton = useRef<HTMLButtonElement>(null);
  const opener = useRef<Element | null>(null);
  const titleId = useId();
  const textId = useId();

  const settle = useCallback((ok: boolean) => {
    requestRef.current?.resolve(ok);
    requestRef.current = null;
    setRequest(null);
    if (opener.current instanceof HTMLElement) opener.current.focus();
  }, []);

  const ask = useCallback(
    (options: ConfirmOptions) =>
      new Promise<boolean>((resolve) => {
        requestRef.current?.resolve(false);
        opener.current = document.activeElement;
        const next = { options, resolve };
        requestRef.current = next;
        setRequest(next);
      }),
    [],
  );

  // The safe choice has the focus, so a stray Enter never confirms a removal.
  useEffect(() => {
    if (request) cancelButton.current?.focus();
  }, [request]);

  useEffect(() => () => requestRef.current?.resolve(false), []);

  const onKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Escape") {
      e.preventDefault();
      settle(false);
    } else if (e.key === "Tab") {
      // Two buttons only: keep the focus inside the dialog.
      e.preventDefault();
      const next = document.activeElement === cancelButton.current ? confirmButton : cancelButton;
      next.current?.focus();
    }
  };

  return (
    <ConfirmContext.Provider value={ask}>
      {children}
      {request && (
        <div
          className="fixed inset-0 z-50 grid place-items-center bg-ink/30 p-4"
          onMouseDown={(e) => e.target === e.currentTarget && settle(false)}
        >
          <div
            role="alertdialog"
            aria-modal="true"
            aria-labelledby={titleId}
            aria-describedby={request.options.text ? textId : undefined}
            onKeyDown={onKeyDown}
            className="anim-fade w-full max-w-sm rounded-3xl border border-line bg-white p-6"
          >
            <h2 id={titleId} className="text-base font-medium">
              {request.options.title}
            </h2>
            {request.options.text && (
              <p id={textId} className="mt-2 text-[13px] text-dim">
                {request.options.text}
              </p>
            )}
            <div className="mt-5 flex justify-end gap-2">
              <button
                ref={cancelButton}
                type="button"
                onClick={() => settle(false)}
                className="inline-flex h-10 items-center rounded-full border border-line bg-white px-5 text-sm font-medium hover:border-[#cfd2d9]"
              >
                Vazgeç
              </button>
              <button
                ref={confirmButton}
                type="button"
                onClick={() => settle(true)}
                className={cn(
                  PRIMARY_BUTTON,
                  request.options.danger && "bg-(--status-danger-fg) text-white",
                )}
              >
                {request.options.confirmLabel}
              </button>
            </div>
          </div>
        </div>
      )}
    </ConfirmContext.Provider>
  );
}
