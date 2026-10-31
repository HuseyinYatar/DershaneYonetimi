package com.dershane.attendanceservice.shared.application.event;

/** Event names on the wire (English PascalCase, see shared/events). */
public enum EventType {
  STUDENT_ABSENT("StudentAbsent"),
  STUDENT_EXCUSED_BY_TEACHER("StudentExcusedByTeacher");

  private final String eventName;

  EventType(String eventName) {
    this.eventName = eventName;
  }

  public String eventName() {
    return eventName;
  }
}
