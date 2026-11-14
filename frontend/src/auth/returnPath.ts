// Where to go after sign-in. Only same-origin paths are accepted (open-redirect guard). The URL
// parser decides, so browser quirks like "/\host" or "/<tab>/host" (tabs and newlines are stripped,
// which turns them into "//host") cannot slip through.
export function returnPath(state: unknown): string {
  if (typeof state !== "string" || !state.startsWith("/")) return "/";
  try {
    const url = new URL(state, window.location.origin);
    if (url.origin !== window.location.origin) return "/";
    return url.pathname + url.search + url.hash;
  } catch {
    return "/";
  }
}

// Passed as the OIDC `state` so the user comes back to the page they were on.
export function currentPath(): string {
  return window.location.pathname + window.location.search + window.location.hash;
}
