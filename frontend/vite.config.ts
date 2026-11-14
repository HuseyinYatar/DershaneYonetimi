import { fileURLToPath } from "node:url";
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: { alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) } },
  // Keycloak redirect URIs allow only :3000; failing is better than silently moving to another port.
  server: { port: 3000, strictPort: true },
  // `npm run preview` (built bundle) must use the same allowed port, not Vite's default 4173.
  preview: { port: 3000, strictPort: true },
  test: { environment: "jsdom" },
});
