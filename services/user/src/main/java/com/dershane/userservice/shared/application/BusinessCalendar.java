package com.dershane.userservice.shared.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Business dates (membership start/end) follow the institution's local calendar, not UTC: a student
 * moved at 01:00 Istanbul time must get today's local date.
 */
@Component
public class BusinessCalendar {

  private final Clock clock;
  private final ZoneId zone;

  public BusinessCalendar(Clock clock, @Value("${user.time-zone:Europe/Istanbul}") String zone) {
    this.clock = clock;
    this.zone = ZoneId.of(zone);
  }

  public LocalDate today() {
    return LocalDate.now(clock.withZone(zone));
  }

  public Instant now() {
    return clock.instant();
  }
}
