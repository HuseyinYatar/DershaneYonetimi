package com.dershane.userservice.shared.domain;

/**
 * What kind of failure an {@link ErrorCode} is. The domain never knows about HTTP; the web adapter
 * translates a category to a status ({@code ErrorStatusMapper}).
 */
public enum ErrorCategory {
  VALIDATION,
  UNAUTHENTICATED,
  FORBIDDEN,
  NOT_FOUND,
  UNSUPPORTED_METHOD,
  CONFLICT,
  RULE_VIOLATION,
  UNAVAILABLE,
  BAD_GATEWAY,
  INTERNAL
}
