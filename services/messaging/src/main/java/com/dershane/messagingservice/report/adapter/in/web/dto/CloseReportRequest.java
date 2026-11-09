package com.dershane.messagingservice.report.adapter.in.web.dto;

import jakarta.validation.constraints.Size;

/** Free text: masked in {@code toString}. */
public record CloseReportRequest(@Size(max = 1000) String resolutionNote) {

  @Override
  public String toString() {
    return "CloseReportRequest[]";
  }
}
