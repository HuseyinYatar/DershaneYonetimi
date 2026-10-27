package com.dershane.userservice.shared.adapter.in.web.idempotency;

import static com.dershane.userservice.support.TestJwts.actor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dershane.userservice.shared.adapter.in.security.ActorProvider;
import com.dershane.userservice.shared.domain.DomainException;
import com.dershane.userservice.shared.domain.ErrorCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class IdempotencyAspectTest {

  private static final String KEY = "create-student-1";

  @Mock private IdempotencyStore store;
  @Mock private ActorProvider actorProvider;
  @Mock private ProceedingJoinPoint joinPoint;
  @Mock private MethodSignature signature;

  private final JsonMapper jsonMapper = JsonMapper.builder().build();
  private final UUID callerId = UUID.randomUUID();
  private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/x");
  private IdempotencyAspect aspect;

  @BeforeEach
  void setUp() throws NoSuchMethodException {
    aspect = new IdempotencyAspect(store, actorProvider, jsonMapper, new SimpleMeterRegistry());
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  }

  @AfterEach
  void tearDown() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void withoutHeaderTheRequestRunsNormally() throws Throwable {
    when(joinPoint.proceed()).thenReturn(ResponseEntity.ok("done"));

    Object result = aspect.handle(joinPoint);

    assertThat(result).isEqualTo(ResponseEntity.ok("done"));
    verifyNoInteractions(store);
  }

  @Test
  void firstRequestStoresTheResponseWithoutTheTemporaryPassword() throws Throwable {
    keyedRequest(Map.of("name", "A"));
    IdempotencyRecord record = record("any-hash");
    when(store.claim(eq(callerId), eq(KEY), anyString())).thenReturn(record);
    CreatedWithSecret body = new CreatedWithSecret("user", "Secret123", false);
    when(joinPoint.proceed()).thenReturn(ResponseEntity.status(201).body(body));

    aspect.handle(joinPoint);

    ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
    verify(store).complete(eq(record.getId()), eq(201), stored.capture());
    JsonNode json = jsonMapper.readTree(stored.getValue());
    assertThat(json.get("temporaryPassword").isNull()).isTrue();
    assertThat(json.get("temporaryPasswordAlreadyIssued").asBoolean()).isTrue();
  }

  @Test
  void retryWithSameBodyReturnsTheStoredResponse() throws Throwable {
    keyedRequest(Map.of("name", "A"));
    AtomicReference<String> hash = new AtomicReference<>();
    when(store.claim(eq(callerId), eq(KEY), anyString()))
        .thenAnswer(
            invocation -> {
              hash.set(invocation.getArgument(2));
              throw new DataIntegrityViolationException("uk_idempotency_caller_key");
            });
    when(store.find(callerId, KEY))
        .thenAnswer(invocation -> Optional.of(completed(hash.get(), "{\"id\":\"42\"}")));

    Object result = aspect.handle(joinPoint);

    ResponseEntity<?> response = (ResponseEntity<?>) result;
    assertThat(response.getStatusCode().value()).isEqualTo(201);
    assertThat(response.getHeaders().getFirst(IdempotencyAspect.REPLAYED_HEADER)).isEqualTo("true");
    assertThat(((JsonNode) response.getBody()).get("id").asString()).isEqualTo("42");
    verify(joinPoint, never()).proceed();
  }

  @Test
  void sameKeyWithDifferentBodyIsRejected() throws Throwable {
    keyedRequest(Map.of("name", "B"));
    when(store.claim(eq(callerId), eq(KEY), anyString()))
        .thenThrow(new DataIntegrityViolationException("uk_idempotency_caller_key"));
    when(store.find(callerId, KEY)).thenReturn(Optional.of(completed("other-hash", "{}")));

    assertThatThrownBy(() -> aspect.handle(joinPoint))
        .isInstanceOf(DomainException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED);
  }

  @Test
  void failedOperationReleasesTheKey() throws Throwable {
    keyedRequest(Map.of("name", "A"));
    IdempotencyRecord record = record("any-hash");
    when(store.claim(eq(callerId), eq(KEY), anyString())).thenReturn(record);
    when(joinPoint.proceed())
        .thenThrow(new DomainException(ErrorCode.IDENTITY_PROVIDER_UNAVAILABLE));

    assertThatThrownBy(() -> aspect.handle(joinPoint)).isInstanceOf(DomainException.class);

    verify(store).release(record.getId());
    verify(store, never()).complete(any(), anyInt(), any());
  }

  private void keyedRequest(Object body) throws NoSuchMethodException {
    request.addHeader(IdempotencyAspect.HEADER, KEY);
    Method method = SampleController.class.getDeclaredMethod("create", Object.class);
    when(actorProvider.require()).thenReturn(actor(callerId, UUID.randomUUID()));
    when(joinPoint.getSignature()).thenReturn(signature);
    when(signature.getMethod()).thenReturn(method);
    when(joinPoint.getArgs()).thenReturn(new Object[] {body});
  }

  private IdempotencyRecord record(String hash) {
    IdempotencyRecord record =
        IdempotencyRecord.start(callerId, KEY, hash, Instant.now(), Instant.now().plusSeconds(60));
    org.springframework.test.util.ReflectionTestUtils.setField(record, "id", UUID.randomUUID());
    return record;
  }

  private IdempotencyRecord completed(String hash, String body) {
    IdempotencyRecord record = record(hash);
    record.complete(201, body);
    return record;
  }

  /** A response whose secret must not be stored (like the temporary password of a new account). */
  record CreatedWithSecret(
      String user, String temporaryPassword, boolean temporaryPasswordAlreadyIssued)
      implements ReplaySafe {

    @Override
    public Object forReplay() {
      return new CreatedWithSecret(user, null, true);
    }
  }

  /** Stands in for a controller method with a request body. */
  static class SampleController {
    @Idempotent
    ResponseEntity<Object> create(@RequestBody Object body) {
      return ResponseEntity.ok(body);
    }
  }
}
