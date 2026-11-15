import { useState } from "react";
import { ApiError } from "@/api/problem";

// One key per form: a double click or retry returns the first response instead of a second record.
// After the server rejects the body for good (4xx) or the record was created, the next attempt gets a new key.
export function useIdempotencyKey() {
  const [key, setKey] = useState(() => crypto.randomUUID());
  const renew = () => setKey(crypto.randomUUID());
  const renewOnClientError = (error: unknown) => {
    if (error instanceof ApiError && error.status < 500) renew();
  };
  return { key, renew, renewOnClientError };
}
