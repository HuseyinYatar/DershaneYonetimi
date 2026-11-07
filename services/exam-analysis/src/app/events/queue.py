"""SQS adapter: the only place that talks to the queue.

The service never subscribes to SNS itself. The queue and its subscription to user-events are created
by Terraform (infra/terraform). Here the worker only reads messages and
deletes the ones it has committed.
"""

from dataclasses import dataclass
from typing import Any, Protocol


@dataclass(frozen=True)
class QueueMessage:
    message_id: str
    # RawMessageDelivery=true on the subscriptions: the body is the producer's envelope JSON as is,
    # not wrapped in an SNS notification document.
    body: str
    # Needed to delete the message; valid only for this receive.
    receipt_handle: str
    # SNS message attributes pass through with raw delivery.
    event_type: str | None
    traceparent: str | None
    # How many times SQS has handed out this message; after maxReceiveCount it moves to the DLQ.
    receive_count: int


class MessageQueue(Protocol):
    def receive(self) -> list[QueueMessage]: ...
    def delete(self, message: QueueMessage) -> None: ...


class SqsQueue:
    def __init__(self, sqs_client: Any, queue_url: str, max_messages: int, wait_seconds: int) -> None:
        self._client = sqs_client
        self._queue_url = queue_url
        self._max_messages = max_messages
        self._wait_seconds = wait_seconds

    def receive(self) -> list[QueueMessage]:
        # Received messages are not removed: they become invisible for the queue's visibility
        # timeout and come back if they are not deleted in time (worker crashed, handler failed).
        response = self._client.receive_message(
            QueueUrl=self._queue_url,
            MaxNumberOfMessages=self._max_messages,
            WaitTimeSeconds=self._wait_seconds,
            MessageAttributeNames=["All"],
            MessageSystemAttributeNames=["ApproximateReceiveCount"],
        )
        return [self._to_message(raw) for raw in response.get("Messages", [])]

    def delete(self, message: QueueMessage) -> None:
        self._client.delete_message(QueueUrl=self._queue_url, ReceiptHandle=message.receipt_handle)

    @staticmethod
    def _to_message(raw: dict[str, Any]) -> QueueMessage:
        attributes = raw.get("MessageAttributes", {})

        def attribute(name: str) -> str | None:
            return attributes.get(name, {}).get("StringValue")

        return QueueMessage(
            message_id=raw["MessageId"],
            body=raw["Body"],
            receipt_handle=raw["ReceiptHandle"],
            event_type=attribute("eventType"),
            traceparent=attribute("traceparent"),
            receive_count=int(raw.get("Attributes", {}).get("ApproximateReceiveCount", "1")),
        )
