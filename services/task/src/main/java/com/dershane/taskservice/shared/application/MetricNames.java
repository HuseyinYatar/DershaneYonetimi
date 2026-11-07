package com.dershane.taskservice.shared.application;

/**
 * Custom metric names. Prometheus exposes them with underscores, e.g. {@code
 * task_assignments_generated_total}. Every metric also carries the common tag {@code
 * application=task}.
 *
 * <p>Names must not end in {@code .created}, {@code .info} or {@code .total}: OpenMetrics reserves
 * those suffixes and the Prometheus registry silently drops such meters.
 */
public final class MetricNames {

  public static final String ASSIGNMENTS_GENERATED = "task.assignments.generated";
  public static final String ASSIGNMENTS_OVERDUE = "task.assignments.overdue";
  public static final String REMINDERS_SENT = "task.reminders.sent";

  /** Tasks whose daily generation failed in a CronJob run. */
  public static final String GENERATION_FAILURES = "task.generation.failures";

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
