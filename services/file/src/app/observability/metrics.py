"""Prometheus metrics on /metrics. Every metric carries application="file" (CLAUDE.md)."""

from prometheus_client import Counter, Histogram

APPLICATION = "file"

HTTP_REQUEST_DURATION = Histogram(
    "http_request_duration_seconds",
    "HTTP request duration by route template",
    ["application", "method", "route", "status"],
)
DOMAIN_ERRORS = Counter("domain_errors_total", "Rejected operations by error code", ["application", "code"])

# outcome: stored / replayed (same Idempotency-Key and same file)
FILE_UPLOADS = Counter(
    "file_uploads_total", "Accepted uploads by purpose and outcome", ["application", "purpose", "outcome"]
)
FILE_UPLOAD_BYTES = Histogram(
    "file_upload_size_bytes",
    "Size of stored files after photos are re-encoded",
    ["application", "purpose"],
    buckets=(64 * 1024, 256 * 1024, 1024**2, 2 * 1024**2, 5 * 1024**2, 10 * 1024**2, 20 * 1024**2),
)
# access: download_url (browser) / content (services)
FILE_READS = Counter(
    "file_reads_total", "Files handed out by purpose and access", ["application", "purpose", "access"]
)
