package com.dershane.userservice.shared.adapter.in.web;

import com.dershane.userservice.shared.domain.ErrorCategory;
import com.dershane.userservice.shared.domain.ErrorCode;
import org.springframework.http.HttpStatus;

/** Translates the HTTP-free {@link ErrorCategory} of an error code to an HTTP status. */
public final class ErrorStatusMapper {

  private ErrorStatusMapper() {}

  public static HttpStatus statusFor(ErrorCode code) {
    return statusFor(code.getCategory());
  }

  public static HttpStatus statusFor(ErrorCategory category) {
    return switch (category) {
      case VALIDATION -> HttpStatus.BAD_REQUEST;
      case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
      case FORBIDDEN -> HttpStatus.FORBIDDEN;
      case NOT_FOUND -> HttpStatus.NOT_FOUND;
      case UNSUPPORTED_METHOD -> HttpStatus.METHOD_NOT_ALLOWED;
      case CONFLICT -> HttpStatus.CONFLICT;
      case RULE_VIOLATION -> HttpStatus.UNPROCESSABLE_CONTENT;
      case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
      case BAD_GATEWAY -> HttpStatus.BAD_GATEWAY;
      case INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
    };
  }
}
