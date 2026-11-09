package com.dershane.messagingservice.shared.application;

/**
 * Custom metric names. Prometheus exposes them with underscores, e.g. {@code
 * messaging_messages_sent_total}. Every metric also carries the common tag {@code
 * application=messaging}.
 *
 * <p>Names must not end in {@code .created}, {@code .info} or {@code .total}: OpenMetrics reserves
 * those suffixes and the Prometheus registry silently drops such meters.
 */
public final class MetricNames {

  /** Tag {@code type}: TEXT or PHOTO. */
  public static final String MESSAGES_SENT = "messaging.messages.sent";

  public static final String REPORTS_CREATED = "messaging.reports.created";

  /** Gauge: WebSocket sessions open on this pod. */
  public static final String WEBSOCKET_SESSIONS = "messaging.websocket.sessions";

  /** Tag {@code outcome}: success or failure. */
  public static final String REALTIME_PUBLISH = "messaging.realtime.publish";

  public static final String BUSINESS_RULE_VIOLATIONS = "business.rule.violations";

  public static final String OUTBOX_EVENTS_PUBLISHED = "outbox.events.published";
  public static final String OUTBOX_EVENTS_FAILED = "outbox.events.failed";
  public static final String OUTBOX_EVENTS_PENDING = "outbox.events.pending";
  public static final String OUTBOX_PUBLISH_LAG = "outbox.publish.lag";

  /**
   * Tags {@code eventType}, {@code result}: {@code processed}, {@code duplicate}, {@code failed}.
   */
  public static final String EVENTS_CONSUMED = "events.consumed";

  public static final String IDEMPOTENCY_REPLAYS = "idempotency.replays";

  private MetricNames() {}
}
