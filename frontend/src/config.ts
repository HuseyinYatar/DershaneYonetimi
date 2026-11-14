export interface AppConfig {
  apiBaseUrl: string;
  keycloakUrl: string;
}

declare global {
  interface Window {
    __CONFIG__?: Partial<AppConfig>;
  }
}

// Read lazily so tests can set window.__CONFIG__ first. Never use import.meta.env for these URLs:
// it is baked in at build time and the same image must run in every environment.
export function getConfig(): AppConfig {
  const { apiBaseUrl, keycloakUrl } = window.__CONFIG__ ?? {};
  if (!apiBaseUrl || !keycloakUrl) {
    throw new Error(
      "window.__CONFIG__ is missing apiBaseUrl or keycloakUrl; check that /config.js is served before the app.",
    );
  }
  return { apiBaseUrl, keycloakUrl };
}
