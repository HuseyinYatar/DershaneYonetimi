package com.dershane.messagingservice.report.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Free text: masked in {@code toString}. */
public record FileReportRequest(
    @NotNull UUID messageId, @NotBlank @Size(max = 1000) String reason) {

  @Override
  public String toString() {
    return "FileReportRequest[messageId=" + messageId + "]";
  }
}
