package com.dershane.userservice.shared.adapter.out.messaging;

import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

/**
 * Publishes one outbox row to the service topic. {@code eventType} is a message attribute so SQS
 * subscriptions can filter, and {@code traceparent} carries the producer's trace to consumers.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SnsEventSender {

  public static final String EVENT_TYPE_ATTRIBUTE = "eventType";
  public static final String TRACEPARENT_ATTRIBUTE = "traceparent";

  private final SnsClient sns;
  private final OutboxProperties properties;

  public void send(OutboxEvent event) {
    Map<String, MessageAttributeValue> attributes = new HashMap<>();
    attributes.put(EVENT_TYPE_ATTRIBUTE, stringAttribute(event.getEventType()));
    if (event.getTraceparent() == null) {
      log.atDebug()
          .addKeyValue("eventId", event.getEventId())
          .log("Event has no traceparent; consumers will start a new trace");
    } else {
      attributes.put(TRACEPARENT_ATTRIBUTE, stringAttribute(event.getTraceparent()));
    }
    PublishResponse response =
        sns.publish(
            PublishRequest.builder()
                .topicArn(properties.topicArn())
                .message(event.getPayload())
                .messageAttributes(attributes)
                .build());
    log.atDebug()
        .addKeyValue("eventId", event.getEventId())
        .addKeyValue("snsMessageId", response.messageId())
        .log("Event published to SNS");
  }

  private static MessageAttributeValue stringAttribute(String value) {
    return MessageAttributeValue.builder().dataType("String").stringValue(value).build();
  }
}
