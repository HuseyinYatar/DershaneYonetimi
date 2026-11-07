"""Liveness probe for the worker: `python -m app.worker.healthcheck` (exit code 0 = alive)."""

import sys
import time
from pathlib import Path

from app.config import get_settings


def main() -> int:
    settings = get_settings()
    heartbeat = Path(settings.worker_heartbeat_file)
    if not heartbeat.exists():
        print(f"worker heartbeat missing: {heartbeat}", file=sys.stderr)
        return 1
    age = time.time() - heartbeat.stat().st_mtime
    if age > settings.worker_heartbeat_max_age_seconds:
        print(f"worker heartbeat is {age:.0f}s old", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
