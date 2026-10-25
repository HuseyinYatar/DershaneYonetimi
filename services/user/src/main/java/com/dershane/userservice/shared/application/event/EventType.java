package com.dershane.userservice.shared.application.event;

/** Event names on the wire (English PascalCase, see shared/events). */
public enum EventType {
  STUDENT_ADDED_TO_CLASSROOM("StudentAddedToClassroom"),
  STUDENT_REMOVED_FROM_CLASSROOM("StudentRemovedFromClassroom"),
  USER_UPDATED("UserUpdated"),
  GUARDIAN_LINKED("GuardianLinked"),
  GUARDIAN_UNLINKED("GuardianUnlinked");

  private final String eventName;

  EventType(String eventName) {
    this.eventName = eventName;
  }

  public String eventName() {
    return eventName;
  }
}
