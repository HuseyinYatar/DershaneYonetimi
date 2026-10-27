package com.dershane.userservice.shared.adapter.in.web.idempotency;

import com.dershane.userservice.shared.adapter.in.security.ActorProvider;
import com.dershane.userservice.shared.application.MetricNames;
import com.dershane.userservice.shared.domain.ErrorCode;
import com.dershane.userservice.shared.domain.Rejections;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.annotation.Annotation;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

/**
 * Implements {@link Idempotent}. Keys are scoped to the caller, so two admins can't collide.
 *
 * <ul>
 *   <li>same key, same body, finished: the stored response is returned (header {@code
 *       Idempotent-Replayed: true})
 *   <li>same key, different body: 422 IDEMPOTENCY_KEY_REUSED
 *   <li>same key while the first request is still running: 409 IDEMPOTENCY_REQUEST_IN_PROGRESS
 *   <li>the operation fails: the key is released so the client can retry
 * </ul>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class IdempotencyAspect {

  public static final String HEADER = "Idempotency-Key";
  public static final String REPLAYED_HEADER = "Idempotent-Replayed";
  private static final int MAX_KEY_LENGTH = 100;

  private final IdempotencyStore store;
  private final ActorProvider actorProvider;
  private final JsonMapper jsonMapper;
  private final MeterRegistry meterRegistry;

  @Around("@annotation(com.dershane.userservice.shared.adapter.in.web.idempotency.Idempotent)")
  public Object handle(ProceedingJoinPoint joinPoint) throws Throwable {
    HttpServletRequest request =
        ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
    String key = request.getHeader(HEADER);
    if (key == null || key.isBlank()) {
      log.atDebug()
          .addKeyValue("http.method", request.getMethod())
          .log("No Idempotency-Key header; processing without idempotency");
      return joinPoint.proceed();
    }
    if (key.length() > MAX_KEY_LENGTH) {
      throw Rejections.reject(
          log, ErrorCode.VALIDATION_FAILED, "Idempotency-Key too long", "length", key.length());
    }
    UUID callerId = actorProvider.require().id();
    String requestHash = hash(request, requestBody(joinPoint));

    IdempotencyRecord record;
    try {
      record = store.claim(callerId, key, requestHash);
    } catch (DataIntegrityViolationException alreadyUsed) {
      return replayOrReject(callerId, key, requestHash);
    }
    return proceedAndStore(joinPoint, record);
  }

  private Object proceedAndStore(ProceedingJoinPoint joinPoint, IdempotencyRecord record)
      throws Throwable {
    Object result;
    try {
      result = joinPoint.proceed();
    } catch (Throwable failure) {
      store.release(record.getId());
      log.atInfo()
          .addKeyValue("idempotencyRecordId", record.getId())
          .log("Operation failed; idempotency key released for retry");
      throw failure;
    }
    if (result instanceof ResponseEntity<?> response
        && response.getStatusCode().is2xxSuccessful()) {
      Object body =
          response.getBody() instanceof ReplaySafe safe ? safe.forReplay() : response.getBody();
      store.complete(
          record.getId(), response.getStatusCode().value(), jsonMapper.writeValueAsString(body));
      log.atDebug().addKeyValue("idempotencyRecordId", record.getId()).log("Response stored");
    } else {
      store.release(record.getId());
      log.atWarn()
          .addKeyValue("idempotencyRecordId", record.getId())
          .log("Idempotent endpoint returned no successful ResponseEntity; key released");
    }
    return result;
  }

  private ResponseEntity<Object> replayOrReject(UUID callerId, String key, String requestHash) {
    IdempotencyRecord existing =
        store
            .find(callerId, key)
            .orElseThrow(
                () ->
                    Rejections.reject(
                        log,
                        ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS,
                        "Idempotency key conflict but no record found (released concurrently)",
                        "callerId",
                        callerId));
    if (!existing.getRequestHash().equals(requestHash)) {
      throw Rejections.reject(
          log,
          ErrorCode.IDEMPOTENCY_KEY_REUSED,
          "Idempotency key reused with a different request",
          "callerId",
          callerId,
          "idempotencyRecordId",
          existing.getId());
    }
    if (existing.getStatus() == IdempotencyRecord.Status.IN_PROGRESS) {
      throw Rejections.reject(
          log,
          ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS,
          "Same idempotent request is still being processed",
          "callerId",
          callerId,
          "idempotencyRecordId",
          existing.getId());
    }
    meterRegistry.counter(MetricNames.IDEMPOTENCY_REPLAYS).increment();
    log.atInfo()
        .addKeyValue("callerId", callerId)
        .addKeyValue("idempotencyRecordId", existing.getId())
        .log("Idempotent replay: returning stored response");
    return ResponseEntity.status(existing.getResponseStatus())
        .header(REPLAYED_HEADER, "true")
        .body(jsonMapper.readTree(existing.getResponseBody()));
  }

  private static Object requestBody(ProceedingJoinPoint joinPoint) {
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    Annotation[][] annotations = signature.getMethod().getParameterAnnotations();
    for (int i = 0; i < annotations.length; i++) {
      for (Annotation annotation : annotations[i]) {
        if (annotation instanceof RequestBody) {
          return joinPoint.getArgs()[i];
        }
      }
    }
    return null;
  }

  private String hash(HttpServletRequest request, Object body) {
    String material =
        request.getMethod()
            + ' '
            + request.getRequestURI()
            + '\n'
            + (body == null ? "" : jsonMapper.writeValueAsString(body));
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(material.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is not available", ex);
    }
  }
}
