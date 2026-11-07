package com.dershane.taskservice.shared.adapter.in.event;

import com.dershane.taskservice.shared.application.MetricNames;
import com.dershane.taskservice.shared.application.port.out.ProcessedEventPort;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads events other services published, from this service's SQS queue, and hands each to its
 * {@link EventHandler}.
 *
 * <p>Delivery is at least once. The event id is recorded in the same transaction as the handler's
 * change, so a repeated delivery is skipped and a failed handler leaves nothing behind. A message
 * is deleted only after that transaction committed; otherwise it stays in the queue and is retried,
 * and after the queue's redrive limit it moves to the dead-letter queue (alert on its depth).
 *
 * <p>Runs on its own thread: a long poll would block the shared scheduler thread that the outbox
 * publisher needs. The producer's trace is continued from the {@code traceparent} attribute.
 */
@Slf4j
@Component
@ConditionalOnBooleanProperty(name = "task.inbox.enabled", matchIfMissing = true)
public class SqsEventConsumer implements SmartLifecycle {

  private static final Propagator.Getter<Map<String, MessageAttributeValue>> ATTRIBUTE_GETTER =
      (attributes, key) -> {
        MessageAttributeValue value = attributes.get(key);
        return value == null ? null : value.stringValue();
      };

  private final SqsClient sqs;
  private final InboxProperties properties;
  private final Map<String, EventHandler> handlers;
  private final ProcessedEventPort processedEvents;
  private final TransactionTemplate transactions;
  private final JsonMapper json;
  private final MeterRegistry meterRegistry;
  private final ObjectProvider<Tracer> tracer;
  private final ObjectProvider<Propagator> propagator;

  private volatile boolean running;
  private volatile Thread poller;
  private String queueUrl;

  public SqsEventConsumer(
      SqsClient sqs,
      InboxProperties properties,
      List<EventHandler> handlers,
      ProcessedEventPort processedEvents,
      TransactionTemplate transactions,
      JsonMapper json,
      MeterRegistry meterRegistry,
      ObjectProvider<Tracer> tracer,
      ObjectProvider<Propagator> propagator) {
    this.sqs = sqs;
    this.properties = properties;
    this.processedEvents = processedEvents;
    this.transactions = transactions;
    this.json = json;
    this.meterRegistry = meterRegistry;
    this.tracer = tracer;
    this.propagator = propagator;
    Map<String, EventHandler> byType = new HashMap<>();
    for (EventHandler handler : handlers) {
      for (String eventType : handler.eventTypes()) {
        if (byType.putIfAbsent(eventType, handler) != null) {
          throw new IllegalStateException("More than one handler for event type " + eventType);
        }
      }
    }
    this.handlers = Map.copyOf(byType);
    log.atInfo()
        .addKeyValue("queue", properties.queueName())
        .addKeyValue("eventTypes", this.handlers.keySet())
        .log("Event consumer enabled");
  }

  @Override
  public void start() {
    running = true;
    poller = Thread.ofVirtual().name("sqs-event-consumer").start(this::pollWhileRunning);
  }

  @Override
  public void stop() {
    running = false;
    Thread thread = poller;
    if (thread == null) {
      return;
    }
    thread.interrupt();
    try {
      if (!thread.join(Duration.ofSeconds(10))) {
        log.warn("Event consumer did not stop within 10 seconds");
      }
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  private void pollWhileRunning() {
    while (running) {
      try {
        pollOnce();
      } catch (RuntimeException ex) {
        if (!running) {
          return;
        }
        log.atWarn()
            .addKeyValue("queue", properties.queueName())
            .addKeyValue("exception", ex.getClass().getSimpleName())
            .addKeyValue("exceptionMessage", ex.getMessage())
            .log("Reading the event queue failed; will retry");
        pause(properties.errorBackoff());
      }
    }
  }

  /** One receive call and the handling of everything it returned. */
  int pollOnce() {
    String url = queueUrl();
    List<Message> messages =
        sqs.receiveMessage(
                request ->
                    request
                        .queueUrl(url)
                        .maxNumberOfMessages(properties.batchSize())
                        .waitTimeSeconds((int) properties.waitTime().toSeconds())
                        .messageAttributeNames("All"))
            .messages();
    messages.forEach(this::consume);
    return messages.size();
  }

  /** The queue may be created after this service starts, so it is looked up until it exists. */
  private String queueUrl() {
    if (queueUrl == null) {
      queueUrl = sqs.getQueueUrl(request -> request.queueName(properties.queueName())).queueUrl();
      log.atInfo().addKeyValue("queue", properties.queueName()).log("Listening to event queue");
    }
    return queueUrl;
  }

  private void consume(Message message) {
    Tracer activeTracer = tracer.getIfAvailable();
    Propagator activePropagator = propagator.getIfAvailable();
    if (activeTracer == null || activePropagator == null) {
      log.debug("No tracer available; event handled without a span");
      handle(message);
      return;
    }
    Span span =
        activePropagator
            .extract(message.messageAttributes(), ATTRIBUTE_GETTER)
            .name("event consume")
            .kind(Span.Kind.CONSUMER)
            .start();
    try (Tracer.SpanInScope ignored = activeTracer.withSpan(span)) {
      handle(message);
    } finally {
      span.end();
    }
  }

  private void handle(Message message) {
    Incoming incoming = parse(message);
    if (incoming == null) {
      return;
    }
    try {
      boolean applied = Boolean.TRUE.equals(transactions.execute(status -> applyOnce(incoming)));
      sqs.deleteMessage(
          request -> request.queueUrl(queueUrl).receiptHandle(message.receiptHandle()));
      count(incoming.eventType(), applied ? "processed" : "duplicate");
      if (applied) {
        log.atInfo()
            .addKeyValue("eventId", incoming.eventId())
            .addKeyValue("eventType", incoming.eventType())
            .log("Event applied");
      } else {
        log.atDebug()
            .addKeyValue("eventId", incoming.eventId())
            .addKeyValue("eventType", incoming.eventType())
            .log("Duplicate event delivery ignored");
      }
    } catch (RuntimeException ex) {
      count(incoming.eventType(), "failed");
      // Event payloads carry personal data (KVKK), and parse errors can quote it: log the
      // exception type only, the stack trace stays at DEBUG.
      log.atWarn()
          .addKeyValue("eventId", incoming.eventId())
          .addKeyValue("eventType", incoming.eventType())
          .addKeyValue("exception", ex.getClass().getSimpleName())
          .log("Applying event failed; message stays in the queue for a retry");
      log.atDebug().setCause(ex).addKeyValue("eventId", incoming.eventId()).log("Event failure");
    }
  }

  private boolean applyOnce(Incoming incoming) {
    if (!processedEvents.markProcessed(incoming.eventId(), incoming.eventType())) {
      return false;
    }
    incoming.handler().handle(incoming.eventType(), incoming.data());
    return true;
  }

  /**
   * Returns null for a message that is not a known event; it stays in the queue (see class doc).
   */
  private Incoming parse(Message message) {
    try {
      JsonNode envelope = json.readTree(message.body());
      UUID eventId = UUID.fromString(envelope.required("eventId").asString());
      String eventType = envelope.required("eventType").asString();
      EventHandler handler = handlers.get(eventType);
      if (handler == null) {
        count("unknown", "failed");
        log.atWarn()
            .addKeyValue("eventId", eventId)
            .addKeyValue("eventType", eventType)
            .log("No handler for event type; check the queue's filter policy in infra/terraform");
        return null;
      }
      return new Incoming(eventId, eventType, handler, envelope.required("data"));
    } catch (RuntimeException ex) {
      count("unknown", "failed");
      log.atWarn()
          .addKeyValue("sqsMessageId", message.messageId())
          .addKeyValue("exception", ex.getClass().getSimpleName())
          .log("Message is not a valid event envelope; it stays in the queue");
      return null;
    }
  }

  private void count(String eventType, String result) {
    meterRegistry
        .counter(MetricNames.EVENTS_CONSUMED, "eventType", eventType, "result", result)
        .increment();
  }

  private static void pause(Duration duration) {
    try {
      Thread.sleep(duration);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }

  private record Incoming(UUID eventId, String eventType, EventHandler handler, JsonNode data) {

    @Override
    public String toString() {
      return "Incoming[eventId=" + eventId + ", eventType=" + eventType + "]";
    }
  }
}
