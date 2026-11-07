package com.dershane.taskservice.assignment.domain;

/** Where an assignment stands. */
public enum AssignmentStatus {
  /** Handed out, not submitted yet, still before the due time. */
  ASSIGNED,
  /** The student submitted (possibly late); not evaluated yet. */
  SUBMITTED,
  /** The due time passed without a submission. A late submission is still accepted. */
  OVERDUE,
  /** The teacher evaluated the submission. */
  GRADED,
  /** The student left the classroom before the due time. */
  CANCELLED
}
