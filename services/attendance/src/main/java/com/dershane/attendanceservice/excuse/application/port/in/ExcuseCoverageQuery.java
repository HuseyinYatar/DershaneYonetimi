package com.dershane.attendanceservice.excuse.application.port.in;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** For other modules: which students have an approved excuse on a day. One query for all. */
public interface ExcuseCoverageQuery {

  /**
   * @return student id to the id of the approved excuse covering {@code date}; students without one
   *     are absent from the map. With several covering excuses the earliest decided one wins.
   */
  Map<UUID, UUID> coveredExcuses(Collection<UUID> studentIds, LocalDate date);
}
