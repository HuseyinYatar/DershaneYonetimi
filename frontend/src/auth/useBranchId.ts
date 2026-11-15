import { useMe } from "@/api/queries/user";
import { useActor } from "./useActor";

// The branch of the signed-in staff member: from the token, else from the profile.
export function useBranchId(): string | undefined {
  const actor = useActor();
  const me = useMe();
  return actor?.branchId ?? me.data?.branchId;
}
