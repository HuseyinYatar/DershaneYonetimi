package com.dershane.taskservice.shared.application.event;

/** Event names on the wire (English PascalCase, see shared/events). */
public enum EventType {
  TASK_ASSIGNED("TaskAssigned"),
  TASK_DUE_SOON("TaskDueSoon"),
  TASK_OVERDUE("TaskOverdue"),
  SUBMISSION_GRADED("SubmissionGraded");

  private final String eventName;

  EventType(String eventName) {
    this.eventName = eventName;
  }

  public String eventName() {
    return eventName;
  }
}
