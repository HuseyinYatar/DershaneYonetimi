package com.dershane.userservice.shared.adapter.out.messaging;

import com.dershane.userservice.shared.application.BusinessCalendar;
import com.dershane.userservice.shared.application.MetricNames;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sends committed outbox rows to SNS.
 *
 * <p>Rows are locked with SKIP LOCKED for the duration of the batch, so pods never send the same
 * row twice. On the first failure the batch stops: later rows of the same student would otherwise
 * overtake an earlier event (e.g. "added to class" before "removed from old class"). The failed row
 * is retried on the next run. Delivery is at-least-once; consumers deduplicate by eventId.
 */
@Slf4j
@Component
@ConditionalOnBooleanProperty(name = "user.outbox.enabled", matchIfMissing = true)
public class OutboxPublisher {

  private final OutboxEventRepository outbox;
  private final SnsEventSender sender;
  private final OutboxProperties properties;
  private final TransactionTemplate transactionTemplate;
  private final BusinessCalendar calendar;
  private final MeterRegistry meterRegistry;

  public OutboxPublisher(
      OutboxEventRepository outbox,
      SnsEventSender sender,
      OutboxProperties properties,
      TransactionTemplate transactionTemplate,
      BusinessCalendar calendar,
      MeterRegistry meterRegistry) {
    this.outbox = outbox;
    this.sender = sender;
    this.properties = properties;
    this.transactionTemplate = transactionTemplate;
    this.calendar = calendar;
    this.meterRegistry = meterRegistry;
    log.atInfo()
        .addKeyValue("topicArn", properties.topicArn())
        .addKeyValue("intervalMs", properties.publishInterval().toMillis())
        .addKeyValue("batchSize", properties.batchSize())
        .log("Outbox publisher enabled");
  }

  @Scheduled(fixedDelayString = "${user.outbox.publish-interval}")
  public void publishPending() {
    Integer published = transactionTemplate.execute(status -> publishBatch());
    log.atDebug().addKeyValue("published", published).log("Outbox publish run finished");
  }

  int publishBatch() {
    List<OutboxEvent> batch = outbox.lockNextBatch(properties.batchSize());
    if (batch.isEmpty()) {
      log.debug("No pending outbox events");
      return 0;
    }
    int published = 0;
    for (OutboxEvent event : batch) {
      if (!publishOne(event)) {
        log.atWarn()
            .addKeyValue("eventId", event.getEventId())
            .addKeyValue("skippedInBatch", batch.size() - published - 1)
            .log("Stopping outbox batch after a failed publish to keep event order");
        break;
      }
      published++;
    }
    log.atInfo()
        .addKeyValue("published", published)
        .addKeyValue("batchSize", batch.size())
        .log("Outbox batch processed");
    return published;
  }

  private boolean publishOne(OutboxEvent event) {
    try {
      sender.send(event);
    } catch (RuntimeException ex) {
      event.markFailed(ex.getClass().getSimpleName() + ": " + ex.getMessage());
      meterRegistry
          .counter(MetricNames.OUTBOX_EVENTS_FAILED, "eventType", event.getEventType())
          .increment();
      // The publisher retries every second while SNS is down; the stack trace stays at DEBUG so
      // the WARN line remains readable.
      log.atWarn()
          .addKeyValue("eventId", event.getEventId())
          .addKeyValue("eventType", event.getEventType())
          .addKeyValue("attempt", event.getAttemptCount())
          .addKeyValue("exception", ex.getClass().getSimpleName())
          .addKeyValue("exceptionMessage", ex.getMessage())
          .log("Publishing outbox event to SNS failed; will retry");
      log.atDebug().setCause(ex).addKeyValue("eventId", event.getEventId()).log("SNS failure");
      return false;
    }
    Instant now = calendar.now();
    event.markPublished(now);
    meterRegistry
        .counter(MetricNames.OUTBOX_EVENTS_PUBLISHED, "eventType", event.getEventType())
        .increment();
    Timer.builder(MetricNames.OUTBOX_PUBLISH_LAG)
        .description("Time from recording an event to publishing it")
        .register(meterRegistry)
        .record(Duration.between(event.getCreatedAt(), now));
    return true;
  }
}
