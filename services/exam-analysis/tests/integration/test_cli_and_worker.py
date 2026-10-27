import json
import threading

import pytest
from sqlalchemy import func, select

from app.cli import DEFAULT_CATALOG, seed_catalog
from app.config import Settings
from app.models import OpticalFormat, ScoreType, ScoreTypeWeight, Topic
from app.schemas.optical import OpticalFieldDefinition
from app.worker import __main__ as worker
from app.worker import healthcheck

pytestmark = pytest.mark.integration


def test_seed_catalog_is_valid_and_idempotent(db_session, caplog) -> None:
    catalog = json.loads(DEFAULT_CATALOG.read_text(encoding="utf-8"))

    seed_catalog(db_session, catalog)
    counts = [
        db_session.scalar(select(func.count()).select_from(model))
        for model in (Topic, ScoreType, ScoreTypeWeight)
    ]
    seed_catalog(db_session, catalog)

    assert [
        db_session.scalar(select(func.count()).select_from(model))
        for model in (Topic, ScoreType, ScoreTypeWeight)
    ] == counts
    for optical_format in db_session.scalars(select(OpticalFormat)):
        OpticalFieldDefinition.model_validate(optical_format.field_definition)
    assert any("seed.weights_unverified" in str(record.msg) for record in caplog.records)


def test_worker_loop_beats_and_stops_on_request(session_factory, tmp_path, mocker) -> None:
    heartbeat = tmp_path / "heartbeat"
    settings = Settings(
        worker_heartbeat_file=str(heartbeat),
        worker_poll_interval_seconds=0.01,
        redis_url=None,
        outbox_relay_enabled=False,
    )
    stop = threading.Event()
    calls = []

    def process_next(self) -> bool:
        calls.append(1)
        if len(calls) == 3:
            stop.set()
        return False

    mocker.patch("app.worker.__main__.ImportProcessor.process_next", process_next)
    mocker.patch("app.worker.__main__.ImportProcessor.requeue_stale", return_value=0)

    worker.run(settings, stop)

    assert len(calls) == 3
    assert heartbeat.exists()
    mocker.patch("app.worker.healthcheck.get_settings", return_value=settings)
    assert healthcheck.main() == 0


def test_worker_survives_a_failing_loop(tmp_path, mocker, caplog) -> None:
    settings = Settings(
        worker_heartbeat_file=str(tmp_path / "hb"), worker_poll_interval_seconds=0.01, redis_url=None
    )
    stop = threading.Event()

    def explode(self) -> bool:
        stop.set()
        raise ConnectionError("database restarting")

    mocker.patch("app.worker.__main__.ImportProcessor.process_next", explode)
    mocker.patch("app.worker.__main__.ImportProcessor.requeue_stale", return_value=0)

    worker.run(settings, stop)

    assert any("worker.loop_failed" in str(record.msg) for record in caplog.records)


def test_healthcheck_fails_without_a_recent_heartbeat(tmp_path, mocker) -> None:
    settings = Settings(worker_heartbeat_file=str(tmp_path / "missing"))
    mocker.patch("app.worker.healthcheck.get_settings", return_value=settings)

    assert healthcheck.main() == 1


def test_relay_requires_a_topic_when_enabled() -> None:
    with pytest.raises(RuntimeError, match="SNS_TOPIC_ARN"):
        worker.build_relay(Settings(outbox_relay_enabled=True, sns_topic_arn=None))
