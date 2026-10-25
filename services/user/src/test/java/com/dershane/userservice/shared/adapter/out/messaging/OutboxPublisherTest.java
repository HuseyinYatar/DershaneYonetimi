package com.dershane.userservice.shared.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dershane.userservice.shared.application.BusinessCalendar;
import com.dershane.userservice.shared.application.MetricNames;
import com.dershane.userservice.support.TestTransactions;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sns.model.SnsException;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

  private static final Instant NOW = Instant.parse("2026-10-17T07:00:00Z");

  @Mock private OutboxEventRepository outbox;
  @Mock private SnsEventSender sender;

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private OutboxPublisher publisher;

  @BeforeEach
  void setUp() {
    OutboxProperties properties =
        new OutboxProperties(
            true,
            "arn:aws:sns:eu-central-1:000000000000:user-events",
            Duration.ofSeconds(1),
            20,
            Duration.ofDays(7));
    publisher =
        new OutboxPublisher(
            outbox,
            sender,
            properties,
            TestTransactions.template(),
            new BusinessCalendar(Clock.fixed(NOW, ZoneOffset.UTC), "Europe/Istanbul"),
            meterRegistry);
  }

  @Test
  void publishesAllPendingEventsAndMarksThem() {
    OutboxEvent first = pending("UserUpdated");
    OutboxEvent second = pending("StudentAddedToClassroom");
    when(outbox.lockNextBatch(20)).thenReturn(List.of(first, second));

    int published = publisher.publishBatch();

    assertThat(published).isEqualTo(2);
    assertThat(first.getPublishedAt()).isEqualTo(NOW);
    assertThat(second.getPublishedAt()).isEqualTo(NOW);
    assertThat(publishedCount("UserUpdated")).isEqualTo(1.0);
    assertThat(meterRegistry.timer(MetricNames.OUTBOX_PUBLISH_LAG).count()).isEqualTo(2);
  }

  @Test
  void stopsAtTheFirstFailureToKeepEventOrder() {
    OutboxEvent first = pending("StudentRemovedFromClassroom");
    OutboxEvent failing = pending("StudentAddedToClassroom");
    OutboxEvent later = pending("UserUpdated");
    when(outbox.lockNextBatch(20)).thenReturn(List.of(first, failing, later));
    doNothing().when(sender).send(first);
    doThrow(SnsException.builder().message("unreachable").build()).when(sender).send(failing);

    int published = publisher.publishBatch();

    assertThat(published).isEqualTo(1);
    assertThat(failing.getPublishedAt()).isNull();
    assertThat(failing.getAttemptCount()).isEqualTo(1);
    assertThat(failing.getLastError()).contains("unreachable");
    assertThat(later.getAttemptCount()).isZero();
    verify(sender, never()).send(later);
    assertThat(
            meterRegistry
                .counter(MetricNames.OUTBOX_EVENTS_FAILED, "eventType", "StudentAddedToClassroom")
                .count())
        .isEqualTo(1.0);
  }

  @Test
  void emptyOutboxPublishesNothing() {
    when(outbox.lockNextBatch(20)).thenReturn(List.of());

    assertThat(publisher.publishBatch()).isZero();
    verify(sender, never()).send(any());
  }

  private static OutboxEvent pending(String type) {
    return OutboxEvent.pending(
        UUID.randomUUID(), type, UUID.randomUUID(), "{}", null, NOW.minusSeconds(2));
  }

  private double publishedCount(String type) {
    return meterRegistry.counter(MetricNames.OUTBOX_EVENTS_PUBLISHED, "eventType", type).count();
  }
}
