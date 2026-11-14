import { useQueryClient } from "@tanstack/react-query";
import { useAuth } from "react-oidc-context";

export function useLogout() {
  const queryClient = useQueryClient();
  const auth = useAuth();
  return () => {
    // Clear cached server data first so the back button after logout shows nothing.
    queryClient.clear();
    void auth.signoutRedirect();
  };
}
