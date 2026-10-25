package com.dershane.userservice.shared.adapter.in.web.idempotency;

/**
 * A response body with fields that must not be stored for idempotent replay (e.g. a temporary
 * password). {@link #forReplay()} returns the version that is safe to persist.
 */
public interface ReplaySafe {

  Object forReplay();
}
