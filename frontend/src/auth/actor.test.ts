import { describe, expect, it } from "vitest";
import { decodeActor } from "./actor";

function token(claims: object): string {
  const b64url = (value: object) =>
    btoa(String.fromCharCode(...new TextEncoder().encode(JSON.stringify(value))))
      .replace(/\+/g, "-")
      .replace(/\//g, "_")
      .replace(/=+$/, "");
  return `${b64url({ alg: "RS256" })}.${b64url(claims)}.signature`;
}

describe("decodeActor", () => {
  it("reads sub, known roles and branch_id", () => {
    const actor = decodeActor(
      token({
        sub: "u-1",
        branch_id: "b-1",
        realm_access: { roles: ["student", "offline_access", "admin"] },
      }),
    );
    expect(actor).toEqual({ id: "u-1", roles: ["student", "admin"], branchId: "b-1" });
  });

  it("handles base64url payloads with non-ASCII characters", () => {
    const actor = decodeActor(
      token({ sub: "u-2", name: "Öğrenci ???>>>", realm_access: { roles: ["teacher"] } }),
    );
    expect(actor?.id).toBe("u-2");
    expect(actor?.roles).toEqual(["teacher"]);
  });

  it("tolerates a missing realm_access", () => {
    expect(decodeActor(token({ sub: "u-3" }))).toEqual({
      id: "u-3",
      roles: [],
      branchId: undefined,
    });
  });

  it("returns null for missing, malformed or sub-less tokens", () => {
    expect(decodeActor(undefined)).toBeNull();
    expect(decodeActor("not-a-token")).toBeNull();
    expect(decodeActor(token({ roles: [] }))).toBeNull();
  });
});
