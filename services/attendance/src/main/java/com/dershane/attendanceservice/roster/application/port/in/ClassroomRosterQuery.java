package com.dershane.attendanceservice.roster.application.port.in;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** For other modules: who was in a classroom on a date. No caller check; the caller decides. */
public interface ClassroomRosterQuery {

  /**
   * Students whose membership in the classroom of the given branch covered {@code date}, ordered by
   * name. The branch is part of the question so a classroom id of another branch yields nothing.
   */
  List<RosterEntryView> rosterOn(UUID classroomId, UUID branchId, LocalDate date);
}
