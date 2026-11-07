package com.dershane.taskservice.roster.application.port.in;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** For other modules: who was in a classroom on a date. No caller check; the caller decides. */
public interface ClassroomRosterQuery {

  /** Students whose membership covered {@code date}, ordered by name. */
  List<RosterEntryView> rosterOn(UUID classroomId, LocalDate date);
}
