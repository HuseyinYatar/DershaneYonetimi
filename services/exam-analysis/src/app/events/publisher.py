import json
from typing import Any, Protocol


class EventPublisher(Protocol):
    """Port to the message broker; swapping SNS for RabbitMQ means one new adapter."""

    def publish(self, envelope: dict[str, Any]) -> None: ...


class SnsEventPublisher:
    """Publishes every event to one topic; subscribers filter on the eventType attribute."""

    def __init__(self, sns_client: Any, topic_arn: str) -> None:
        self._client = sns_client
        self._topic_arn = topic_arn

    def publish(self, envelope: dict[str, Any]) -> None:
        self._client.publish(
            TopicArn=self._topic_arn,
            Message=json.dumps(envelope),
            MessageAttributes={"eventType": {"DataType": "String", "StringValue": envelope["eventType"]}},
        )


class InMemoryEventPublisher:
    def __init__(self) -> None:
        self.published: list[dict[str, Any]] = []

    def publish(self, envelope: dict[str, Any]) -> None:
        self.published.append(envelope)
