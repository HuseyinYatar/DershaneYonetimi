package com.dershane.attendanceservice.attendance.application;

import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Attendance rules that deployments may tune ({@code attendance.*} in application.yml).
 *
 * @param takeEarlyMinutes how long before a lesson starts the teacher may already submit
 * @param correctionWindowDays how many days after the session day a teacher may still correct a
 *     record; later only an admin
 */
@Validated
@ConfigurationProperties("attendance")
public record AttendanceProperties(Session session, @Min(0) int correctionWindowDays) {

  public record Session(@Min(0) int takeEarlyMinutes) {}

  public Duration takeEarly() {
    return Duration.ofMinutes(session.takeEarlyMinutes());
  }
}
