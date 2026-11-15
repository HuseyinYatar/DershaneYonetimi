package com.dershane.taskservice.shared.adapter.in.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dershane.taskservice.shared.application.MetricNames;
import com.dershane.taskservice.shared.application.port.out.ProcessedEventPort;
import com.dershane.taskservice.support.TestTransactions;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class SqsEventConsumerTest {

  private static final String TYPE = "StudentAddedToClassroom";
  private static final String QUEUE_URL = "http://localhost:4566/000000000000/task-user-events";

  // The consumer calls the Consumer-lambda overloads of the client, so those are what is stubbed.
  @Mock private SqsClient sqs;

  @Captor private ArgumentCaptor<Consumer<DeleteMessageRequest.Builder>> deleteRequest;

  @Mock private ProcessedEventPort processedEvents;
  @Mock private EventHandler handler;
  @Mock private ObjectProvider<Tracer> tracer;
  @Mock private ObjectProvider<Propagator> propagator;

  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final UUID eventId = UUID.randomUUID();
  private SqsEventConsumer consumer;

  @BeforeEach
  void setUp() {
    when(handler.eventType()).thenReturn(TYPE);
    consumer = newConsumer(List.of(handler));
  }

  private SqsEventConsumer newConsumer(List<EventHandler> handlers) {
    return new SqsEventConsumer(
        sqs,
        new InboxProperties(
            true, "task-user-events", Duration.ofSeconds(1), 10, Duration.ofSeconds(1)),
        handlers,
        processedEvents,
        TestTransactions.template(),
        JsonMapper.builder().build(),
        meters,
        tracer,
        propagator);
  }

  private void receive(String body) {
    Message message = Message.builder().messageId("m1").receiptHandle("r1").body(body).build();
    when(sqs.getQueueUrl(ArgumentMatchers.<Consumer<GetQueueUrlRequest.Builder>>any()))
        .thenReturn(GetQueueUrlResponse.builder().queueUrl(QUEUE_URL).build());
    when(sqs.receiveMessage(ArgumentMatchers.<Consumer<ReceiveMessageRequest.Builder>>any()))
        .thenReturn(ReceiveMessageResponse.builder().messages(message).build());
  }

  private void verifyNothingDeleted() {
    verify(sqs, never())
        .deleteMessage(ArgumentMatchers.<Consumer<DeleteMessageRequest.Builder>>any());
  }

  private String envelope(String eventType) {
    return """
        {"eventId":"%s","eventType":"%s","schemaVersion":1,"occurredAt":"2026-11-08T10:00:00Z",
         "source":"user","data":{"k":"v"}}
        """
        .formatted(eventId, eventType);
  }

  private double count(String eventType, String result) {
    return meters
        .counter(MetricNames.EVENTS_CONSUMED, "eventType", eventType, "result", result)
        .count();
  }

  private void verifyDeleted() {
    verify(sqs).deleteMessage(deleteRequest.capture());
    DeleteMessageRequest.Builder builder = DeleteMessageRequest.builder();
    deleteRequest.getValue().accept(builder);
    DeleteMessageRequest request = builder.build();
    assertThat(request.queueUrl()).isEqualTo(QUEUE_URL);
    assertThat(request.receiptHandle()).isEqualTo("r1");
  }

  @Test
  void handsTheEnvelopeTimeToTheHandler() {
    receive(envelope(TYPE));
    when(processedEvents.markProcessed(eventId, TYPE)).thenReturn(true);

    consumer.pollOnce();

    verify(handler)
        .handle(
            org.mockito.ArgumentMatchers.eq(TYPE),
            org.mockito.ArgumentMatchers.eq(java.time.Instant.parse("2026-11-08T10:00:00Z")),
            any());
  }

  @Test
  void appliesANewEventAndDeletesItsMessage() {
    receive(envelope(TYPE));
    when(processedEvents.markProcessed(eventId, TYPE)).thenReturn(true);

    int received = consumer.pollOnce();

    assertThat(received).isEqualTo(1);
    verify(handler).handle(any(), any(), any());
    verifyDeleted();
    assertThat(count(TYPE, "processed")).isEqualTo(1.0);
  }

  @Test
  void skipsADuplicateDeliveryButStillDeletesIt() {
    receive(envelope(TYPE));
    when(processedEvents.markProcessed(eventId, TYPE)).thenReturn(false);

    consumer.pollOnce();

    verify(handler, never()).handle(any(), any(), any());
    verifyDeleted();
    assertThat(count(TYPE, "duplicate")).isEqualTo(1.0);
  }

  @Test
  void keepsTheMessageWhenTheHandlerFails() {
    receive(envelope(TYPE));
    when(processedEvents.markProcessed(eventId, TYPE)).thenReturn(true);
    doThrow(new IllegalStateException("boom")).when(handler).handle(any(), any(), any());

    consumer.pollOnce();

    verifyNothingDeleted();
    assertThat(count(TYPE, "failed")).isEqualTo(1.0);
  }

  @Test
  void keepsAMessageOfATypeNobodyHandles() {
    receive(envelope("UserUpdated"));

    consumer.pollOnce();

    verify(processedEvents, never()).markProcessed(any(), any());
    verifyNothingDeleted();
    assertThat(count("unknown", "failed")).isEqualTo(1.0);
  }

  @Test
  void keepsAMessageThatIsNotAnEnvelope() {
    receive("not json");

    consumer.pollOnce();

    verify(handler, never()).handle(any(), any(), any());
    verifyNothingDeleted();
    assertThat(count("unknown", "failed")).isEqualTo(1.0);
  }

  @Test
  void refusesTwoHandlersForOneEventType() {
    EventHandler other = org.mockito.Mockito.mock(EventHandler.class);
    when(other.eventType()).thenReturn(TYPE);

    assertThatIllegalStateException()
        .isThrownBy(() -> newConsumer(List.of(handler, other)))
        .withMessageContaining(TYPE);
  }
}
