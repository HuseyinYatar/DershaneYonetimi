package com.dershane.messagingservice.report.adapter.in.web.dto;

import java.time.Instant;

/** Free text: masked in {@code toString}. */
public record AuditEditResponse(String previousContent, Instant editedAt) {

  @Override
  public String toString() {
    return "AuditEditResponse[editedAt=" + editedAt + "]";
  }
}
