package com.dershane.userservice.support;

import io.floci.testcontainers.FlociContainer;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * An SNS topic with an SQS queue subscribed to it on Floci, like a consumer service would have.
 * Reads messages back as (body, attributes), whether or not raw delivery is honoured.
 */
public final class EventQueue {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final SnsClient sns;
  private final SqsClient sqs;
  private final String topicArn;
  private final String queueUrl;

  private EventQueue(SnsClient sns, SqsClient sqs, String topicArn, String queueUrl) {
    this.sns = sns;
    this.sqs = sqs;
    this.topicArn = topicArn;
    this.queueUrl = queueUrl;
  }

  public static EventQueue create(FlociContainer floci) {
    StaticCredentialsProvider credentials =
        StaticCredentialsProvider.create(
            AwsBasicCredentials.create(floci.getAccessKey(), floci.getSecretKey()));
    URI endpoint = URI.create(floci.getEndpoint());
    Region region = Region.of(floci.getRegion());
    SnsClient sns =
        SnsClient.builder()
            .endpointOverride(endpoint)
            .region(region)
            .credentialsProvider(credentials)
            .build();
    SqsClient sqs =
        SqsClient.builder()
            .endpointOverride(endpoint)
            .region(region)
            .credentialsProvider(credentials)
            .build();

    String suffix = UUID.randomUUID().toString().substring(0, 8);
    String topicArn = sns.createTopic(b -> b.name("user-events-" + suffix)).topicArn();
    String queueUrl = sqs.createQueue(b -> b.queueName("consumer-" + suffix)).queueUrl();
    String queueArn =
        sqs.getQueueAttributes(
                b -> b.queueUrl(queueUrl).attributeNames(QueueAttributeName.QUEUE_ARN))
            .attributes()
            .get(QueueAttributeName.QUEUE_ARN);
    sns.subscribe(
        SubscribeRequest.builder()
            .topicArn(topicArn)
            .protocol("sqs")
            .endpoint(queueArn)
            .attributes(Map.of("RawMessageDelivery", "true"))
            .build());
    return new EventQueue(sns, sqs, topicArn, queueUrl);
  }

  public String topicArn() {
    return topicArn;
  }

  public SnsClient snsClient() {
    return sns;
  }

  /** Polls until {@code count} messages arrived or the timeout passed. */
  public List<Received> receive(int count, Duration timeout) {
    List<Received> received = new ArrayList<>();
    Instant deadline = Instant.now().plus(timeout);
    while (received.size() < count && Instant.now().isBefore(deadline)) {
      List<Message> messages =
          sqs.receiveMessage(
                  b ->
                      b.queueUrl(queueUrl)
                          .maxNumberOfMessages(10)
                          .waitTimeSeconds(1)
                          .messageAttributeNames("All"))
              .messages();
      for (Message message : messages) {
        received.add(Received.from(message));
        sqs.deleteMessage(b -> b.queueUrl(queueUrl).receiptHandle(message.receiptHandle()));
      }
    }
    return received;
  }

  /** A delivered event: the envelope JSON and the SNS message attributes. */
  public record Received(JsonNode envelope, Map<String, String> attributes) {

    static Received from(Message message) {
      JsonNode body = JSON.readTree(message.body());
      if (body.has("Type") && body.has("Message")) {
        // SNS envelope (raw delivery not applied): unwrap message and attributes.
        Map<String, String> attributes = new HashMap<>();
        JsonNode snsAttributes = body.path("MessageAttributes");
        snsAttributes
            .propertyNames()
            .forEach(name -> attributes.put(name, snsAttributes.get(name).get("Value").asString()));
        return new Received(JSON.readTree(body.get("Message").asString()), attributes);
      }
      Map<String, String> attributes = new HashMap<>();
      message
          .messageAttributes()
          .forEach((name, value) -> attributes.put(name, stringValue(value)));
      return new Received(body, attributes);
    }

    private static String stringValue(MessageAttributeValue value) {
      return value.stringValue();
    }

    public String eventType() {
      return envelope.get("eventType").asString();
    }

    public JsonNode data() {
      return envelope.get("data");
    }
  }
}
