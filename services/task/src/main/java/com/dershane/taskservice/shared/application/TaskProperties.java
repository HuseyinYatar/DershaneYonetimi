package com.dershane.taskservice.shared.application;

import jakarta.validation.constraints.Min;
import java.time.Duration;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Task rules that deployments may tune ({@code task.*} in application.yml). It lives in {@code
 * shared} because several modules read it and {@code shared} must not depend on a business module.
 *
 * @param timeZone the institution's time zone, used to turn dates and times into instants
 * @param maxAttachments how many attachment files one task may carry
 * @param reminder reminder settings
 */
@Validated
@ConfigurationProperties("task")
public record TaskProperties(
    @DefaultValue("Europe/Istanbul") ZoneId timeZone,
    @DefaultValue("10") @Min(1) int maxAttachments,
    @DefaultValue Reminder reminder) {

  /**
   * @param leadTime an assignment gets one reminder this long before it is due
   */
  public record Reminder(@DefaultValue("6h") Duration leadTime) {}
}
