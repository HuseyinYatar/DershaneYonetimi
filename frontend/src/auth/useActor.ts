import { useMemo } from "react";
import { useAuth } from "react-oidc-context";
import { decodeActor, type Actor } from "./actor";

export function useActor(): Actor | null {
  const token = useAuth().user?.access_token;
  return useMemo(() => decodeActor(token), [token]);
}
