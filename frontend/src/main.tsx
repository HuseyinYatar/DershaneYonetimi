import { QueryClientProvider } from "@tanstack/react-query";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router";
import { Toaster } from "sonner";
import { queryClient } from "@/api/queryClient";
import { AuthRoot } from "@/auth/AuthRoot";
import { ConfirmProvider } from "@/components/ui/ConfirmProvider";
import "@fontsource-variable/inter";
import { App } from "./App";
import "./index.css";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <BrowserRouter>
      <AuthRoot>
        <QueryClientProvider client={queryClient}>
          <ConfirmProvider>
            <App />
          </ConfirmProvider>
          <Toaster />
        </QueryClientProvider>
      </AuthRoot>
    </BrowserRouter>
  </StrictMode>,
);
