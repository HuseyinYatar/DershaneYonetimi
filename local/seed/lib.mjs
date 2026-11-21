// Shared helpers of the seed: Keycloak login (auth code + PKCE), Keycloak admin, gateway calls.
// SEED_TARGET=local (default): the docker compose stack, with the known local passwords.
// SEED_TARGET=prod: the cluster (k8s/platform/demo-seed); addresses and passwords come from the environment,
// and the docker helpers (psql, compose) are not available.
import { createHash, randomBytes } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

export const TARGET = process.env.SEED_TARGET ?? 'local';
export const LOCAL = TARGET === 'local';
export const GATEWAY = process.env.GATEWAY_URL ?? 'http://localhost:8000';
export const KEYCLOAK = process.env.KEYCLOAK_URL ?? 'http://localhost:8180';
// The login redirect must be one of the dershane-frontend client's redirect URIs (realm file).
export const FRONTEND = process.env.FRONTEND_URL ?? 'http://localhost:3000';
const KEYCLOAK_ADMIN = {
  user: process.env.KEYCLOAK_ADMIN_USER ?? 'admin',
  password: process.env.KEYCLOAK_ADMIN_PASSWORD ?? 'admin',
};
export const REALM = 'dershane';
export const COMPOSE_FILE = fileURLToPath(new URL('../docker-compose.yml', import.meta.url));
export const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
export const log = (...a) => console.log(...a);

/** Retries fn with backoff while it throws (Keycloak or a service briefly unreachable). */
export async function retry(fn, { tries = 8, delay = 1000, what = 'call' } = {}) {
  let last;
  for (let i = 0; i < tries; i++) {
    try {
      return await fn();
    } catch (e) {
      last = e;
      if (e.fatal) throw e;
      await sleep(delay * Math.min(2 ** i, 8));
    }
  }
  throw new Error(`${what} failed after ${tries} tries: ${last?.message}`);
}

const b64url = (buf) => buf.toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');

/** Minimal cookie jar: name=value pairs from Set-Cookie headers. */
function jarOf() {
  const jar = new Map();
  return {
    absorb(res) {
      for (const c of res.headers.getSetCookie?.() ?? []) {
        const [pair] = c.split(';');
        const i = pair.indexOf('=');
        jar.set(pair.slice(0, i), pair.slice(i + 1));
      }
    },
    header: () => [...jar].map(([k, v]) => `${k}=${v}`).join('; '),
  };
}

const tokenCache = new Map();

/** Logs in through the real Keycloak login page and returns an access token (cached until near expiry). */
export async function login(username, password) {
  const cached = tokenCache.get(username);
  if (cached && cached.exp - Date.now() > 30_000) return cached.token;
  const token = await retry(() => loginOnce(username, password), { what: `login ${username}` });
  const exp = JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString()).exp * 1000;
  tokenCache.set(username, { token, exp });
  return token;
}

async function loginOnce(username, password) {
  const base = `${KEYCLOAK}/realms/${REALM}/protocol/openid-connect`;
  const verifier = b64url(randomBytes(48));
  const challenge = b64url(createHash('sha256').update(verifier).digest());
  const redirect = `${FRONTEND}/cb`;
  const jar = jarOf();
  const authUrl = `${base}/auth?${new URLSearchParams({
    client_id: 'dershane-frontend',
    redirect_uri: redirect,
    response_type: 'code',
    scope: 'openid',
    code_challenge: challenge,
    code_challenge_method: 'S256',
  })}`;
  const page = await fetch(authUrl, { redirect: 'manual' });
  jar.absorb(page);
  const html = await page.text();
  // The theme may change ids and classes; the form that holds the password input is the login form.
  const form = [...html.matchAll(/<form\b[^>]*>[\s\S]*?<\/form>/gi)]
    .map((m) => m[0])
    .find((f) => /name=["']password["']/.test(f));
  const action = form?.match(/<form\b[^>]*\baction=["']([^"']+)["']/i)?.[1]?.replace(/&amp;/g, '&');
  if (!action) throw new Error(`Keycloak login form not found (HTTP ${page.status})`);
  const post = await fetch(action, {
    method: 'POST',
    redirect: 'manual',
    headers: { 'content-type': 'application/x-www-form-urlencoded', cookie: jar.header() },
    body: new URLSearchParams({ username, password, credentialId: '' }),
  });
  const loc = post.headers.get('location') ?? '';
  const code = loc.startsWith(redirect) ? new URL(loc).searchParams.get('code') : null;
  if (!code) {
    throw Object.assign(new Error(`login of ${username} failed (HTTP ${post.status}); wrong password or required action?`), {
      fatal: post.status < 500,
    });
  }
  const tok = await fetch(`${base}/token`, {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'authorization_code',
      client_id: 'dershane-frontend',
      code,
      redirect_uri: redirect,
      code_verifier: verifier,
    }),
  });
  if (!tok.ok) throw new Error(`token exchange failed: HTTP ${tok.status}`);
  return (await tok.json()).access_token;
}

/** Gateway call. Retries on 429/502/503/504. Returns parsed JSON (null for 204 or ok404). */
export async function api(token, method, path, body, { headers = {}, form, ok404 = false } = {}) {
  for (let attempt = 0; ; attempt++) {
    let res;
    try {
      res = await fetch(`${GATEWAY}/api/v1${path}`, {
        method,
        headers: {
          authorization: `Bearer ${token}`,
          'accept-language': 'en',
          ...(body ? { 'content-type': 'application/json' } : {}),
          ...headers,
        },
        body: form ?? (body ? JSON.stringify(body) : undefined),
      });
    } catch (e) {
      if (attempt < 6) {
        await sleep(1000 * (attempt + 1));
        continue;
      }
      throw e;
    }
    if ([429, 502, 503, 504].includes(res.status) && attempt < 6) {
      await sleep(700 * (attempt + 1));
      continue;
    }
    if (res.status === 404 && ok404) return null;
    const text = await res.text();
    if (!res.ok) {
      const err = new Error(`${method} ${path} -> HTTP ${res.status}: ${text.slice(0, 400)}`);
      err.status = res.status;
      try {
        const j = JSON.parse(text);
        err.code = j.errorCode ?? j.code;
      } catch {
        /* not JSON */
      }
      throw err;
    }
    return text ? JSON.parse(text) : null;
  }
}

/** Lists every page of a collection endpoint (Spring page: {content}; FastAPI: plain array or {items}). */
export async function listAll(token, path, query = {}) {
  const out = [];
  for (let page = 0; page < 50; page++) {
    const q = new URLSearchParams({ ...query, page: String(page), size: '100' });
    const res = await api(token, 'GET', `${path}?${q}`);
    const rows = Array.isArray(res) ? res : (res.content ?? res.items ?? []);
    out.push(...rows);
    if (Array.isArray(res) || rows.length < 100) break;
  }
  return out;
}

// --- Keycloak admin (master realm) -------------------------------------------------------------
async function adminToken() {
  return retry(
    async () => {
      const r = await fetch(`${KEYCLOAK}/realms/master/protocol/openid-connect/token`, {
        method: 'POST',
        headers: { 'content-type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({
          grant_type: 'password',
          client_id: 'admin-cli',
          username: KEYCLOAK_ADMIN.user,
          password: KEYCLOAK_ADMIN.password,
        }),
      });
      if (!r.ok) throw new Error(`Keycloak admin login failed: HTTP ${r.status}`);
      return (await r.json()).access_token;
    },
    { what: 'keycloak admin login' },
  );
}

/** Finds a Keycloak account of the realm; returns the admin headers and the account's URL. */
async function keycloakUser(username) {
  const t = await adminToken();
  const h = { authorization: `Bearer ${t}`, 'content-type': 'application/json' };
  const url = `${KEYCLOAK}/admin/realms/${REALM}/users`;
  const users = await (await fetch(`${url}?username=${encodeURIComponent(username)}&exact=true`, { headers: h })).json();
  if (!users.length) throw new Error(`Keycloak user ${username} not found`);
  return { h, url, id: users[0].id };
}

/** Sets a known password on a Keycloak account. temporary=true forces the change-password page at next login. */
export async function setPassword(username, password, temporary) {
  const { h, url, id } = await keycloakUser(username);
  const r = await fetch(`${url}/${id}/reset-password`, {
    method: 'PUT',
    headers: h,
    body: JSON.stringify({ type: 'password', value: password, temporary }),
  });
  if (!r.ok) throw new Error(`reset-password ${username}: HTTP ${r.status} ${await r.text()}`);
  if (!temporary) {
    // A permanent password must not leave UPDATE_PASSWORD (or VERIFY_EMAIL) behind, or login stops at that page.
    const u = await (await fetch(`${url}/${id}`, { headers: h })).json();
    const put = await fetch(`${url}/${id}`, {
      method: 'PUT',
      headers: h,
      body: JSON.stringify({ ...u, requiredActions: [], emailVerified: true }),
    });
    if (!put.ok) throw new Error(`clearing required actions of ${username}: HTTP ${put.status}`);
  }
}

/** Required actions of an account, e.g. ['UPDATE_PASSWORD'] while its password is still temporary. */
export async function requiredActionsOf(username) {
  const { h, url, id } = await keycloakUser(username);
  const u = await (await fetch(`${url}/${id}`, { headers: h })).json();
  return u.requiredActions ?? [];
}

/** Puts required actions back on an account (prod: the super-admin must change its first password). */
export async function setRequiredActions(username, actions) {
  const { h, url, id } = await keycloakUser(username);
  const u = await (await fetch(`${url}/${id}`, { headers: h })).json();
  const put = await fetch(`${url}/${id}`, { method: 'PUT', headers: h, body: JSON.stringify({ ...u, requiredActions: actions }) });
  if (!put.ok) throw new Error(`setting required actions of ${username}: HTTP ${put.status}`);
}

/** Calls fn until it succeeds or the time is up (prod: Keycloak and the services may still be starting). */
export async function until(what, fn, { timeoutMs = 20 * 60_000, everyMs = 10_000 } = {}) {
  const end = Date.now() + timeoutMs;
  for (;;) {
    try {
      return await fn();
    } catch (e) {
      if (Date.now() > end) throw new Error(`Timed out waiting for ${what}: ${e.message}`);
      log(`waiting for ${what}: ${e.message.slice(0, 120)}`);
      await sleep(everyMs);
    }
  }
}

// --- docker helpers, local only, where the API cannot do it (documented in local/README.md) -------
function localOnly(what) {
  if (!LOCAL) throw new Error(`${what} is a local docker helper; not available with SEED_TARGET=${TARGET}`);
}

export function psql(db, sql) {
  localOnly('psql');
  return execFileSync('docker', ['exec', 'dershane-postgres-1', 'psql', '-U', 'postgres', '-d', db, '-tAq', '-c', sql], {
    encoding: 'utf8',
  }).trim();
}

export function compose(...args) {
  localOnly('docker compose');
  return execFileSync('docker', ['compose', '-f', COMPOSE_FILE, ...args], {
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'pipe'],
  });
}

export async function waitFor(what, check, { timeoutMs = 90_000, everyMs = 1500 } = {}) {
  const end = Date.now() + timeoutMs;
  let last;
  while (Date.now() < end) {
    last = await check();
    if (last.done) return;
    await sleep(everyMs);
  }
  throw new Error(`Timed out waiting for ${what}: ${last?.info ?? ''}`);
}
