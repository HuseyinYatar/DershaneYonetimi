package com.dershane.attendanceservice.excuse.adapter.in.web.dto;

import jakarta.validation.constraints.Size;

/** The note is optional free text: masked in {@code toString}. */
public record DecideExcuseRequest(@Size(max = 500) String note) {

  @Override
  public String toString() {
    return "DecideExcuseRequest[]";
  }
}
