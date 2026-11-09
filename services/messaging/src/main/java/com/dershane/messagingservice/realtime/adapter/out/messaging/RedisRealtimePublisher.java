package com.dershane.messagingservice.realtime.adapter.out.messaging;

import com.dershane.messagingservice.conversation.application.port.in.MessageView;
import com.dershane.messagingservice.conversation.application.port.out.RealtimeEvent;
import com.dershane.messagingservice.conversation.application.port.out.RealtimePublisherPort;
import com.dershane.messagingservice.shared.application.MessagingProperties;
import com.dershane.messagingservice.shared.application.MetricNames;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publishes frames on one Redis channel; every pod listens and delivers to the recipients that are
 * connected to it. Pub/Sub does not replay, so a lost frame is recovered by the client's sync
 * query.
 *
 * <p>shortcut: one channel carries every frame to every pod; switch to a channel per user when pod
 * count or traffic makes that fan-out costly.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class RedisRealtimePublisher implements RealtimePublisherPort {

  private final StringRedisTemplate redis;
  private final JsonMapper json;
  private final MessagingProperties properties;
  private final MeterRegistry meters;

  @Override
  public void publish(RealtimeEvent event) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      // Only after the commit: a frame must never announce a message that was rolled back.
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              send(event);
            }
          });
    } else {
      send(event);
    }
  }

  private void send(RealtimeEvent event) {
    try {
      Map<String, Object> envelope = new LinkedHashMap<>();
      envelope.put("recipients", event.recipients());
      envelope.put("frame", frameOf(event));
      redis.convertAndSend(properties.realtime().channel(), json.writeValueAsString(envelope));
      meters.counter(MetricNames.REALTIME_PUBLISH, "outcome", "success").increment();
    } catch (RuntimeException e) {
      meters.counter(MetricNames.REALTIME_PUBLISH, "outcome", "failure").increment();
      // The data is stored; the client catches up with its sync query after reconnecting.
      log.atWarn()
          .addKeyValue("exception", e.getClass().getSimpleName())
          .log("realtime.publish_failed");
      log.atDebug().setCause(e).log("realtime.publish_failed.cause");
    }
  }

  private static Map<String, Object> frameOf(RealtimeEvent event) {
    Map<String, Object> frame = new LinkedHashMap<>();
    switch (event) {
      case RealtimeEvent.MessageChanged changed -> {
        MessageView message = changed.message();
        frame.put("type", "MESSAGE");
        frame.put("message", message);
      }
      case RealtimeEvent.ReadAdvanced read -> {
        frame.put("type", "READ");
        frame.put("conversationId", read.conversationId());
        frame.put("userId", read.userId());
        frame.put("lastReadSeq", read.lastReadSeq());
      }
    }
    return frame;
  }
}
