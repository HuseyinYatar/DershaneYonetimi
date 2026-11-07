package com.dershane.taskservice.task.domain;

/** How often a task is handed out. */
public enum Recurrence {
  /** One assignment on the start date, due on the end date. */
  ONCE,
  /** One assignment every day of the date range. */
  DAILY,
  /** One assignment on the chosen weekdays of the date range. */
  WEEKLY
}
