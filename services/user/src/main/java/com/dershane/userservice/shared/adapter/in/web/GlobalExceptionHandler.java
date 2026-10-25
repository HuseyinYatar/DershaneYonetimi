package com.dershane.userservice.shared.adapter.in.web;

import com.dershane.userservice.shared.application.MetricNames;
import com.dershane.userservice.shared.domain.ConstraintErrorCodes;
import com.dershane.userservice.shared.domain.DomainException;
import com.dershane.userservice.shared.domain.ErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Converts exceptions to RFC 9457 problem details.
 *
 * <p>Logging policy: {@link DomainException}s are already logged where they are thrown, so they are
 * only counted here. Everything else (framework validation, integrity races, unexpected errors) is
 * logged here, because no guard clause saw it first.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private final ProblemDetailFactory problems;
  private final MeterRegistry meterRegistry;

  public GlobalExceptionHandler(ProblemDetailFactory problems, MeterRegistry meterRegistry) {
    this.problems = problems;
    this.meterRegistry = meterRegistry;
  }

  @ExceptionHandler(DomainException.class)
  public ResponseEntity<ProblemDetail> handleDomainException(DomainException ex) {
    ErrorCode code = ex.getErrorCode();
    if (ErrorStatusMapper.statusFor(code).is4xxClientError()) {
      meterRegistry.counter(MetricNames.BUSINESS_RULE_VIOLATIONS, "rule", code.name()).increment();
    }
    return respond(code);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
    log.atWarn()
        .addKeyValue("errorCode", ErrorCode.ACCESS_DENIED.name())
        .log("Request rejected by method security: caller lacks the required role");
    return respond(ErrorCode.ACCESS_DENIED);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ProblemDetail> handleIntegrityViolation(
      DataIntegrityViolationException ex) {
    String constraint = constraintName(ex);
    ErrorCode code = ConstraintErrorCodes.forConstraint(constraint).orElse(ErrorCode.DATA_CONFLICT);
    if (code == ErrorCode.DATA_CONFLICT) {
      log.atError()
          .setCause(ex)
          .addKeyValue("constraint", constraint)
          .log("Unmapped data integrity violation");
    } else {
      log.atWarn()
          .addKeyValue("errorCode", code.name())
          .addKeyValue("constraint", constraint)
          .log("Concurrent request violated a database constraint");
    }
    return respond(code);
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  public ResponseEntity<ProblemDetail> handleOptimisticLock(OptimisticLockingFailureException ex) {
    log.atWarn()
        .addKeyValue("errorCode", ErrorCode.CONCURRENT_MODIFICATION.name())
        .log("Record was modified by another request (optimistic lock)");
    return respond(ErrorCode.CONCURRENT_MODIFICATION);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
    log.atError()
        .setCause(ex)
        .addKeyValue("errorCode", ErrorCode.INTERNAL_ERROR.name())
        .log("Unexpected error while handling request");
    return respond(ErrorCode.INTERNAL_ERROR);
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<Map<String, String>> errors =
        ex.getBindingResult().getFieldErrors().stream().map(this::toFieldError).toList();
    // Field names only: rejected values may contain personal data.
    log.atWarn()
        .addKeyValue("errorCode", ErrorCode.VALIDATION_FAILED.name())
        .addKeyValue(
            "fields",
            ex.getBindingResult().getFieldErrors().stream().map(FieldError::getField).toList())
        .log("Request body failed validation");
    ProblemDetail problem = problems.create(ErrorCode.VALIDATION_FAILED);
    problem.setProperty("errors", errors);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
  }

  @Override
  protected ResponseEntity<Object> handleHandlerMethodValidationException(
      HandlerMethodValidationException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    log.atWarn()
        .addKeyValue("errorCode", ErrorCode.VALIDATION_FAILED.name())
        .addKeyValue("violations", ex.getParameterValidationResults().size())
        .log("Request parameters failed validation");
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(problems.create(ErrorCode.VALIDATION_FAILED));
  }

  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    log.atWarn()
        .addKeyValue("errorCode", ErrorCode.MALFORMED_REQUEST.name())
        .log("Request body could not be parsed");
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(problems.create(ErrorCode.MALFORMED_REQUEST));
  }

  /** Remaining Spring MVC exceptions (404 route, 405 method, missing parameter, ...). */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex,
      Object body,
      HttpHeaders headers,
      HttpStatusCode statusCode,
      WebRequest request) {
    ErrorCode code = codeForStatus(statusCode);
    log.atWarn()
        .addKeyValue("errorCode", code.name())
        .addKeyValue("exception", ex.getClass().getSimpleName())
        .addKeyValue("httpStatus", statusCode.value())
        .log("Request rejected by Spring MVC");
    ProblemDetail problem = problems.create(code);
    problem.setStatus(statusCode.value());
    return ResponseEntity.status(statusCode).headers(headers).body(problem);
  }

  private ResponseEntity<ProblemDetail> respond(ErrorCode code) {
    return ResponseEntity.status(ErrorStatusMapper.statusFor(code)).body(problems.create(code));
  }

  private Map<String, String> toFieldError(FieldError error) {
    String message = error.getDefaultMessage() == null ? "" : error.getDefaultMessage();
    return Map.of("field", error.getField(), "message", message);
  }

  private static ErrorCode codeForStatus(HttpStatusCode status) {
    return switch (status.value()) {
      case 400 -> ErrorCode.MALFORMED_REQUEST;
      case 404 -> ErrorCode.RESOURCE_NOT_FOUND;
      case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
      default -> ErrorCode.INTERNAL_ERROR;
    };
  }

  private static String constraintName(Throwable ex) {
    Throwable current = ex;
    while (current != null) {
      if (current instanceof ConstraintViolationException cve) {
        return cve.getConstraintName();
      }
      current = current.getCause();
    }
    return null;
  }
}
