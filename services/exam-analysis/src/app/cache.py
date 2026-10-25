"""Cache-aside for report endpoints. Redis is optional: when it is down the service logs a
warning, counts the error and reads from PostgreSQL."""

import json
import uuid
from collections.abc import Iterable
from typing import Any, Protocol

import redis
import structlog

from app.observability.metrics import CACHE_REQUESTS

logger = structlog.get_logger(__name__)


def exam_analysis_key(mock_exam_id: uuid.UUID) -> str:
    return f"exam-analysis:{mock_exam_id}"


def progress_key(student_id: uuid.UUID, exam_type_id: uuid.UUID) -> str:
    return f"progress:{student_id}:{exam_type_id}"


class ResultCache(Protocol):
    def get_json(self, key: str) -> Any | None: ...

    def set_json(self, key: str, value: Any) -> None: ...

    def invalidate_exam(
        self, mock_exam_id: uuid.UUID, exam_type_id: uuid.UUID, student_ids: Iterable[uuid.UUID]
    ) -> None: ...


class RedisResultCache:
    def __init__(self, client: redis.Redis, ttl_seconds: int) -> None:
        self._client = client
        self._ttl = ttl_seconds

    def get_json(self, key: str) -> Any | None:
        try:
            raw = self._client.get(key)
        except redis.RedisError as exc:
            CACHE_REQUESTS.labels(result="error").inc()
            logger.warning("cache.unavailable", operation="get", error=type(exc).__name__)
            return None
        CACHE_REQUESTS.labels(result="miss" if raw is None else "hit").inc()
        return None if raw is None else json.loads(raw)

    def set_json(self, key: str, value: Any) -> None:
        try:
            self._client.set(key, json.dumps(value), ex=self._ttl)
        except redis.RedisError as exc:
            CACHE_REQUESTS.labels(result="error").inc()
            logger.warning("cache.unavailable", operation="set", error=type(exc).__name__)

    def invalidate_exam(
        self, mock_exam_id: uuid.UUID, exam_type_id: uuid.UUID, student_ids: Iterable[uuid.UUID]
    ) -> None:
        keys = [exam_analysis_key(mock_exam_id), *(progress_key(sid, exam_type_id) for sid in student_ids)]
        try:
            self._client.delete(*keys)
        except redis.RedisError as exc:
            CACHE_REQUESTS.labels(result="error").inc()
            # Stale entries expire with the TTL; the warning makes the window visible.
            logger.warning(
                "cache.invalidation_failed",
                mock_exam_id=str(mock_exam_id),
                keys=len(keys),
                error=type(exc).__name__,
                ttl_seconds=self._ttl,
            )


class DisabledResultCache:
    """Used when REDIS_URL is empty; logged once at startup."""

    def get_json(self, key: str) -> Any | None:
        return None

    def set_json(self, key: str, value: Any) -> None:
        return None

    def invalidate_exam(
        self, mock_exam_id: uuid.UUID, exam_type_id: uuid.UUID, student_ids: Iterable[uuid.UUID]
    ) -> None:
        return None


def build_cache(redis_url: str | None, ttl_seconds: int) -> ResultCache:
    if redis_url is None:
        logger.info("cache.disabled", reason="redis_url_empty")
        return DisabledResultCache()
    return RedisResultCache(redis.Redis.from_url(redis_url, socket_timeout=0.5), ttl_seconds)
