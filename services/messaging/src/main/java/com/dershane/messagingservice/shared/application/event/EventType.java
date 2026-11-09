package com.dershane.messagingservice.shared.application.event;

/** Event names on the wire (English PascalCase, see shared/events). */
public enum EventType {
  NEW_MESSAGE("NewMessage");

  private final String eventName;

  EventType(String eventName) {
    this.eventName = eventName;
  }

  public String eventName() {
    return eventName;
  }
}
