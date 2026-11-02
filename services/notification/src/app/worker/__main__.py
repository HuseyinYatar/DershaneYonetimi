"""Background worker: `python -m app.worker`.

Same image as the API, separate Deployment. One loop, two steps:
  A. consume: read the queue, write replicas and notification rows, delete committed messages
  B. deliver: send pending e-mail/SMS notifications, schedule retries
A slow or broken SMTP server only delays step B; events keep being consumed.
"""

import signal
import threading
from pathlib import Path
from types import FrameType

import structlog
from prometheus_client import start_http_server

from app.aws import create_client
from app.config import Settings, get_settings
from app.db import get_engine, get_session_factory
from app.delivery.dispatcher import Dispatcher
from app.delivery.senders import LoggingSmsSender, SmtpEmailSender
from app.events.consumer import EventConsumer
from app.events.handlers import EventHandlers
from app.events.queue import SqsQueue
from app.observability.logging import configure_logging
from app.observability.metrics import WORKER_LAST_LOOP
from app.observability.tracing import configure_tracing

logger = structlog.get_logger(__name__)


# mesaj okuyacak nesneyi hazırlıyor
def build_consumer(settings: Settings) -> EventConsumer | None:
    if settings.sqs_queue_name is None:
        logger.warning(
            "consumer.disabled",
            reason="sqs_queue_name_empty",
            effect="no events are consumed; only stored notifications are delivered",
        )
        return None
    client = create_client("sqs", settings)
    # Fails (and the container restarts) if the queue does not exist yet: floci-init / Terraform
    # must have created it.
    queue_url = client.get_queue_url(QueueName=settings.sqs_queue_name)["QueueUrl"]
    logger.info("consumer.queue_resolved", queue_name=settings.sqs_queue_name)
    queue = SqsQueue(client, queue_url, settings.sqs_max_messages, settings.sqs_wait_seconds)
    return EventConsumer(get_session_factory(), queue, EventHandlers(settings.frontend_base_url))


def run(settings: Settings, stop: threading.Event) -> None:
    consumer = build_consumer(settings)
    dispatcher = Dispatcher(get_session_factory(), SmtpEmailSender(settings), LoggingSmsSender(), settings)
    heartbeat = Path(settings.worker_heartbeat_file)

    logger.info("worker.started", consuming=consumer is not None)
    while not stop.is_set():
        heartbeat.touch()
        WORKER_LAST_LOOP.set_to_current_time()
        worked = False
        failed = False
        try:
            if consumer is not None:
                worked = consumer.consume_batch() > 0
            worked = dispatcher.deliver_batch() > 0 or worked
        except Exception:
            # e.g. the database or Floci is briefly unreachable; the loop keeps running and retries.
            failed = True
            logger.exception("worker.loop_failed")
        # receive_message already waited (long polling) when the queue was empty; sleep only when
        # there is no consumer or the loop failed, so errors do not spin the CPU.
        if failed or (not worked and consumer is None):
            stop.wait(settings.worker_poll_interval_seconds)
    logger.info("worker.stopped")


def main() -> None:
    settings = get_settings()
    configure_logging(settings.log_level, f"{settings.service_name}-worker", settings.environment)
    configure_tracing(settings, engine=get_engine())  # opentlemetry ile alaklaı
    start_http_server(settings.worker_metrics_port)

    stop = threading.Event()

    def request_stop(signum: int, _: FrameType | None) -> None:
        logger.info("worker.stop_requested", signal=signal.Signals(signum).name)
        stop.set()

    signal.signal(signal.SIGTERM, request_stop)
    signal.signal(signal.SIGINT, request_stop)
    run(settings, stop)


if __name__ == "__main__":
    main()
