package com.dershane.userservice.shared.application;

/**
 * Custom metric names. Prometheus exposes them with underscores, e.g. {@code
 * user_registrations_total}. Every metric also carries the common tag {@code application=user}.
 *
 * <p>Names must not end in {@code .created}, {@code .info} or {@code .total}: OpenMetrics reserves
 * those suffixes and the Prometheus registry silently drops such meters.
 */
public final class MetricNames {

  public static final String USER_REGISTRATIONS = "user.registrations";
  public static final String USER_DEACTIVATIONS = "user.deactivations";
  public static final String BUSINESS_RULE_VIOLATIONS = "business.rule.violations";

  public static final String OUTBOX_EVENTS_PUBLISHED = "outbox.events.published";
  public static final String OUTBOX_EVENTS_FAILED = "outbox.events.failed";
  public static final String OUTBOX_EVENTS_PENDING = "outbox.events.pending";
  public static final String OUTBOX_PUBLISH_LAG = "outbox.publish.lag";

  public static final String IDENTITY_COMPENSATIONS = "identity.compensations";
  public static final String IDEMPOTENCY_REPLAYS = "idempotency.replays";

  private MetricNames() {}
}
