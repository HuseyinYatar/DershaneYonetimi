package com.dershane.userservice.shared.adapter.out.messaging;

import com.dershane.userservice.shared.application.BusinessCalendar;
import com.dershane.userservice.shared.application.event.IntegrationEvent;
import com.dershane.userservice.shared.application.port.out.IntegrationEventPublisher;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes domain events to the outbox inside the caller's transaction, so an event exists if and
 * only if the business change was committed. {@link OutboxPublisher} sends them to SNS later.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventRecorder implements IntegrationEventPublisher {

  private final OutboxEventRepository outbox;
  private final JsonMapper jsonMapper;
  private final BusinessCalendar calendar;
  private final ObjectProvider<Tracer> tracer;

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public UUID publish(IntegrationEvent event) {
    UUID eventId = UUID.randomUUID();
    Instant now = calendar.now();
    String eventName = event.type().eventName();
    EventEnvelope envelope =
        new EventEnvelope(
            eventId, eventName, EventEnvelope.SCHEMA_VERSION, now, EventEnvelope.SOURCE, event);
    String payload = jsonMapper.writeValueAsString(envelope);
    outbox.save(
        OutboxEvent.pending(
            eventId, eventName, event.aggregateId(), payload, currentTraceparent(), now));
    log.atInfo()
        .addKeyValue("eventId", eventId)
        .addKeyValue("eventType", eventName)
        .addKeyValue("aggregateId", event.aggregateId())
        .log("Domain event recorded in outbox");
    return eventId;
  }

  /** W3C trace context of the current request, so consumers can join the producer's trace. */
  private String currentTraceparent() {
    Tracer activeTracer = tracer.getIfAvailable();
    Span span = activeTracer == null ? null : activeTracer.currentSpan();
    if (span == null) {
      log.debug("No active span; outbox event stored without traceparent");
      return null;
    }
    TraceContext context = span.context();
    String flags = Boolean.TRUE.equals(context.sampled()) ? "01" : "00";
    return "00-" + context.traceId() + "-" + context.spanId() + "-" + flags;
  }
}
