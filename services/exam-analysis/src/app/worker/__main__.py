"""Background worker: `python -m app.worker`.

Same image as the API, separate Deployment (scaled by HPA). Each loop: claim and process one
import job, requeue jobs of workers that died, relay outbox events to SNS.
"""

import signal
import threading
import time
from pathlib import Path
from types import FrameType

import structlog
from prometheus_client import start_http_server

from app.aws import create_client
from app.cache import build_cache
from app.config import Settings, get_settings
from app.db import get_engine, get_session_factory
from app.events.publisher import SnsEventPublisher
from app.events.relay import OutboxRelay
from app.observability.logging import configure_logging
from app.observability.metrics import WORKER_LAST_LOOP
from app.observability.tracing import configure_tracing
from app.services.import_processor import ImportProcessor
from app.storage.file_storage import S3FileStorage

logger = structlog.get_logger(__name__)

def build_relay(settings: Settings) -> OutboxRelay | None:
    if not settings.outbox_relay_enabled:
        logger.info(
            "outbox.relay_disabled", reason="outbox_relay_enabled_false", effect="events stay in outbox_event"
        )
        return None
    if settings.sns_topic_arn is None:
        raise RuntimeError("OUTBOX_RELAY_ENABLED requires SNS_TOPIC_ARN")
    publisher = SnsEventPublisher(create_client("sns", settings), settings.sns_topic_arn)
    return OutboxRelay(get_session_factory(), publisher, settings.outbox_batch_size)


def run(settings: Settings, stop: threading.Event) -> None:
    session_factory = get_session_factory()
    processor = ImportProcessor(
        session_factory,
        S3FileStorage(create_client("s3", settings), settings.optical_files_bucket),
        build_cache(settings.redis_url, settings.cache_ttl_seconds),
    )
    relay = build_relay(settings)
    heartbeat = Path(settings.worker_heartbeat_file)
    next_reap = 0.0

    logger.info("worker.started", poll_interval_seconds=settings.worker_poll_interval_seconds)
    while not stop.is_set():
        heartbeat.touch()
        WORKER_LAST_LOOP.set_to_current_time()
        worked = False
        try:
            if time.monotonic() >= next_reap:
                processor.requeue_stale(settings.worker_stale_job_timeout_seconds)
                next_reap = time.monotonic() + settings.worker_reaper_interval_seconds
            worked = processor.process_next()
            if relay is not None:
                worked = relay.relay_batch() > 0 or worked
        except Exception:
            # e.g. the database is briefly unreachable; the loop keeps running and retries.
            logger.exception("worker.loop_failed")
        if not worked:
            stop.wait(settings.worker_poll_interval_seconds)
    logger.info("worker.stopped")


def main() -> None:
    settings = get_settings()
    configure_logging(settings.log_level, f"{settings.service_name}-worker", settings.environment)
    configure_tracing(settings, engine=get_engine())
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
