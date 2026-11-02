package com.dershane.apigateway.web;

import org.springframework.http.HttpStatus;

/** Errors the gateway itself produces (upstream services return their own error codes). */
public enum ErrorCode {
  AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED),
  ACCESS_DENIED(HttpStatus.FORBIDDEN),
  ROUTE_NOT_FOUND(HttpStatus.NOT_FOUND),
  RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
  SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
  UPSTREAM_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

  private final HttpStatus status;

  ErrorCode(HttpStatus status) {
    this.status = status;
  }

  public HttpStatus status() {
    return status;
  }

  public String messageKey() {
    return "error." + name();
  }
}
