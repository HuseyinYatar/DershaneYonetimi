"""Prometheus metrics. The API exposes them on /metrics, the worker on its own port."""

from prometheus_client import Counter, Gauge, Histogram

HTTP_REQUEST_DURATION = Histogram(
    "http_request_duration_seconds",
    "HTTP request duration by route template",
    ["method", "route", "status"],
)
DOMAIN_ERRORS = Counter("domain_errors_total", "Rejected operations by error code", ["code"])

IMPORT_JOBS = Counter("import_jobs_total", "Finished import jobs by final status", ["status"])
IMPORT_JOB_DURATION = Histogram(
    "import_job_duration_seconds",
    "Time spent processing one optical import job",
    buckets=(0.5, 1, 2, 5, 10, 20, 30, 60, 120, 300),
)
IMPORT_JOB_QUEUE_WAIT = Histogram(
    "import_job_queue_wait_seconds",
    "Time between an import job being queued and a worker claiming it",
    buckets=(0.5, 1, 2, 5, 10, 30, 60, 120, 300, 600),
)
IMPORT_JOBS_REQUEUED = Counter(
    "import_jobs_requeued_total", "PROCESSING jobs returned to the queue after a worker stopped"
)
IMPORT_ROWS = Counter(
    "import_rows_total", "Optical rows by outcome; reason is empty for matched rows", ["result", "reason"]
)

SCORE_CALCULATION_SKIPPED = Counter(
    "score_calculation_skipped_total", "Score types left without an estimated score", ["reason"]
)
WEAK_TOPIC_RECOMPUTE_SKIPPED = Counter(
    "weak_topic_recompute_skipped_total", "Weak topic recomputations that could not run", ["reason"]
)

OUTBOX_PENDING = Gauge("outbox_pending", "Outbox events not yet published")
OUTBOX_PUBLISHED = Counter("outbox_published_total", "Outbox events published", ["event_type"])
OUTBOX_PUBLISH_FAILURES = Counter(
    "outbox_publish_failures_total", "Failed outbox publish attempts", ["event_type"]
)

CACHE_REQUESTS = Counter("cache_requests_total", "Cache lookups by result (hit/miss/error)", ["result"])

WORKER_LAST_LOOP = Gauge("worker_last_loop_timestamp_seconds", "Unix time of the last worker loop")
