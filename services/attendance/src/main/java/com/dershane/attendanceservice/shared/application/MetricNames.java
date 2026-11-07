package com.dershane.attendanceservice.shared.application;

/**
 * Custom metric names. Prometheus exposes them with underscores, e.g. {@code
 * attendance_submissions_total}. Every metric also carries the common tag {@code
 * application=attendance}.
 *
 * <p>Names must not end in {@code .created}, {@code .info} or {@code .total}: OpenMetrics reserves
 * those suffixes and the Prometheus registry silently drops such meters.
 */
public final class MetricNames {

  /** Tag {@code result}: {@code taken}, {@code rejected}. */
  public static final String ATTENDANCE_SUBMISSIONS = "attendance.submissions";

  /** Tag {@code status}: the status a session reached (completed, cancelled, expired, created). */
  public static final String ATTENDANCE_SESSIONS = "attendance.sessions";

  public static final String ATTENDANCE_CORRECTIONS = "attendance.corrections";
  public static final String ABSENCES_PUBLISHED = "attendance.absences.published";
  public static final String EXCUSED_PUBLISHED = "attendance.excused.published";

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
