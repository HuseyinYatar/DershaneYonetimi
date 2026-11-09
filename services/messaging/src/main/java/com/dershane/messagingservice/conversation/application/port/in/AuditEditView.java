package com.dershane.messagingservice.conversation.application.port.in;

import java.time.Instant;

/** An earlier text of an edited message. Free text: masked in {@code toString}. */
public record AuditEditView(String previousContent, Instant editedAt) {

  @Override
  public String toString() {
    return "AuditEditView[editedAt=" + editedAt + "]";
  }
}
