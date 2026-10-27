package com.dershane.userservice.shared.adapter.out.messaging;

import com.dershane.userservice.shared.application.BusinessCalendar;
import com.dershane.userservice.shared.application.MetricNames;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Pending-events gauge and KVKK retention cleanup of published rows. */
@Slf4j
@Component
public class OutboxMaintenance {

  private final OutboxEventRepository outbox;
  private final OutboxProperties properties;
  private final BusinessCalendar calendar;

  public OutboxMaintenance(
      OutboxEventRepository outbox,
      OutboxProperties properties,
      BusinessCalendar calendar,
      MeterRegistry meterRegistry) {
    this.outbox = outbox;
    this.properties = properties;
    this.calendar = calendar;
    // A growing value means SNS is unreachable or the publisher is disabled.
    Gauge.builder(
            MetricNames.OUTBOX_EVENTS_PENDING,
            outbox,
            OutboxEventRepository::countByPublishedAtIsNull)
        .description("Outbox events not yet published to SNS")
        .register(meterRegistry);
    if (!properties.enabled()) {
      log.warn("Outbox publisher is disabled on this instance; events stay in the outbox table");
    }
  }

  @Transactional
  @Scheduled(cron = "${user.outbox.cleanup-cron}")
  public void deleteExpiredPublishedEvents() {
    Instant cutoff = calendar.now().minus(properties.retention());
    int deleted = outbox.deletePublishedBefore(cutoff);
    log.atInfo()
        .addKeyValue("deleted", deleted)
        .addKeyValue("cutoff", cutoff)
        .log("Deleted published outbox events past retention");
  }
}
