package com.dershane.attendanceservice.attendance.adapter.in.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * The full list of the session: every student of the roster exactly once.
 *
 * @param reason required only when an admin takes attendance after the session day ended; free
 *     text, so it is masked in {@code toString}
 */
public record TakeAttendanceRequest(
    @NotEmpty @Size(max = 500) @Valid List<AttendanceEntryRequest> entries,
    @Size(max = 500) String reason) {

  @Override
  public String toString() {
    return "TakeAttendanceRequest[entries=" + (entries == null ? 0 : entries.size()) + "]";
  }
}
