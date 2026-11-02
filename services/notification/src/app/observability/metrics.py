"""Prometheus metrics. The API exposes them on /metrics, the worker on its own port."""

from prometheus_client import Counter, Gauge, Histogram

HTTP_REQUEST_DURATION = Histogram(
    "http_request_duration_seconds",
    "HTTP request duration by route template",
    ["method", "route", "status"],
)
DOMAIN_ERRORS = Counter("domain_errors_total", "Rejected operations by error code", ["code"])

# outcome: processed / duplicate / invalid / unknown_type / failed
EVENTS_CONSUMED = Counter(
    "notification_events_consumed_total",
    "Queue messages by event type and outcome",
    ["event_type", "outcome"],
)
REPLICA_EVENTS_IGNORED = Counter(
    "notification_replica_events_ignored_total",
    "Replica events that changed nothing (older version, link already replaced)",
    ["event_type", "reason"],
)

NOTIFICATIONS_CREATED = Counter("notifications_created_total", "Notification rows written", ["channel"])
# reason: recipient_missing / inactive / no_address / preference_off
NOTIFICATIONS_SKIPPED = Counter(
    "notifications_skipped_total", "Recipient/channel pairs no notification was written for", ["reason"]
)
# outcome: sent / retry / failed
DELIVERIES = Counter(
    "notification_deliveries_total", "E-mail and SMS delivery attempts", ["channel", "outcome"]
)
NOTIFICATIONS_PENDING = Gauge("notifications_pending", "E-mail and SMS notifications waiting for delivery")

WORKER_LAST_LOOP = Gauge("worker_last_loop_timestamp_seconds", "Unix time of the last worker loop")
