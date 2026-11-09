package com.dershane.messagingservice.report.application.port.in;

import java.util.UUID;

/** Free text: masked in {@code toString}. */
public record FileReportCommand(UUID messageId, String reason) {

  @Override
  public String toString() {
    return "FileReportCommand[messageId=" + messageId + "]";
  }
}
