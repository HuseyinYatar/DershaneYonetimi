package com.dershane.userservice.shared.domain;

import lombok.Getter;

/**
 * A request or operation rejected for a known reason. It is always logged where it is thrown (see
 * {@link Rejections}), so the web adapter only converts it to a problem detail and does not log
 * again.
 */
@Getter
public class DomainException extends RuntimeException {

  private final ErrorCode errorCode;

  public DomainException(ErrorCode errorCode) {
    super(errorCode.name());
    this.errorCode = errorCode;
  }

  public DomainException(ErrorCode errorCode, Throwable cause) {
    super(errorCode.name(), cause);
    this.errorCode = errorCode;
  }
}
