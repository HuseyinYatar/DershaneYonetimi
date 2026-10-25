package com.dershane.userservice.shared.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.dershane.userservice.support.EventQueue;
import com.dershane.userservice.support.EventQueue.Received;
import com.dershane.userservice.support.TestContainers;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The SNS adapter against Floci: a consumer's SQS queue receives payload and attributes. */
class SnsEventSenderIT {

  private static EventQueue queue;

  @BeforeAll
  static void setUp() {
    queue = EventQueue.create(TestContainers.floci());
  }

  @Test
  void consumerQueueReceivesPayloadWithEventTypeAndTraceparent() {
    String traceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
    String payload = "{\"eventId\":\"x\",\"eventType\":\"UserUpdated\",\"data\":{}}";
    OutboxEvent event =
        OutboxEvent.pending(
            UUID.randomUUID(),
            "UserUpdated",
            UUID.randomUUID(),
            payload,
            traceparent,
            Instant.now());
    SnsEventSender sender =
        new SnsEventSender(
            queue.snsClient(),
            new OutboxProperties(
                true, queue.topicArn(), Duration.ofSeconds(1), 20, Duration.ofDays(7)));

    sender.send(event);

    List<Received> received = queue.receive(1, Duration.ofSeconds(15));
    assertThat(received).hasSize(1);
    assertThat(received.getFirst().eventType()).isEqualTo("UserUpdated");
    assertThat(received.getFirst().attributes())
        .containsEntry(SnsEventSender.EVENT_TYPE_ATTRIBUTE, "UserUpdated")
        .containsEntry(SnsEventSender.TRACEPARENT_ATTRIBUTE, traceparent);
  }
}
