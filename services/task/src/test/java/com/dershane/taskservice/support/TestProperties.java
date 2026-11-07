package com.dershane.taskservice.support;

import com.dershane.taskservice.shared.application.TaskProperties;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Builds {@link TaskProperties} for unit tests that do not start Spring. */
public final class TestProperties {

  private TestProperties() {}

  public static TaskProperties utc() {
    return inZone(ZoneOffset.UTC, 10);
  }

  public static TaskProperties inZone(ZoneId zone, int maxAttachments) {
    return new TaskProperties(
        zone, maxAttachments, new TaskProperties.Reminder(Duration.ofHours(6)));
  }
}
