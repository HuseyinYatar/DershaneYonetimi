import { createContext, useContext } from "react";

export interface ConfirmOptions {
  title: string;
  text?: string;
  confirmLabel: string;
  /** Red confirm button, for removals and other things that are hard to undo. */
  danger?: boolean;
}

// Resolves true when the user confirms, false on cancel, Escape or a click outside.
export type Confirm = (options: ConfirmOptions) => Promise<boolean>;

export const ConfirmContext = createContext<Confirm | null>(null);

// In-app replacement for window.confirm: `if (await ask({ title, confirmLabel })) …`
export function useConfirm(): Confirm {
  const ask = useContext(ConfirmContext);
  if (!ask) throw new Error("useConfirm needs a <ConfirmProvider> above it");
  return ask;
}
