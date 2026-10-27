import json
from typing import Any, Protocol


class EventPublisher(Protocol):
    """Port to the message broker; swapping SNS for RabbitMQ means one new adapter."""
    def publish(self, envelope: dict[str, Any], traceparent: str | None = None) -> None: ...


class SnsEventPublisher:
    """Publishes to this service's topic (exam-analysis-events).

    Message attributes: eventType, which subscriptions filter on, and traceparent, which carries the
    producer's trace to consumers (shared/events/README.md).
    """

    def __init__(self, sns_client: Any, topic_arn: str) -> None:
        self._client = sns_client
        self._topic_arn = topic_arn

    def publish(self, envelope: dict[str, Any], traceparent: str | None = None) -> None:
        attributes = {"eventType": {"DataType": "String", "StringValue": envelope["eventType"]}}
        if traceparent is not None:
            attributes["traceparent"] = {"DataType": "String", "StringValue": traceparent}
            # topice mesaj buradan gidiyor.
        self._client.publish(
            TopicArn=self._topic_arn, Message=json.dumps(envelope), MessageAttributes=attributes
        )


class InMemoryEventPublisher:
    def __init__(self) -> None:
        self.published: list[dict[str, Any]] = []

    def publish(self, envelope: dict[str, Any], traceparent: str | None = None) -> None:
        self.published.append(envelope)
