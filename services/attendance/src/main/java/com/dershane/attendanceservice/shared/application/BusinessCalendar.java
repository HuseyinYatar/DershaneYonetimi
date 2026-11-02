package com.dershane.attendanceservice.shared.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Business dates (session days, excuse ranges) follow the institution's local calendar, not UTC: a
 * lesson at 00:30 Istanbul time belongs to the local day, not the UTC day before.
 */
@Component
public class BusinessCalendar {

  private final Clock clock;
  private final ZoneId zone;

  public BusinessCalendar(
      Clock clock, @Value("${attendance.time-zone:Europe/Istanbul}") String zone) {
    this.clock = clock;
    this.zone = ZoneId.of(zone);
  }

  public LocalDate today() {
    return LocalDate.now(clock.withZone(zone));
  }

  /** Wall-clock date and time at the institution, for rules like "15 minutes before the lesson". */
  public LocalDateTime localNow() {
    return LocalDateTime.now(clock.withZone(zone));
  }

  public Instant now() {
    return clock.instant();
  }
}
