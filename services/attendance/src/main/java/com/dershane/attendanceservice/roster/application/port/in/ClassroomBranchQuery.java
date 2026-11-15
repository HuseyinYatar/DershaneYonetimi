package com.dershane.attendanceservice.roster.application.port.in;

import java.util.UUID;

/** For other modules: which branch a classroom belongs to, as far as the membership copy knows. */
public interface ClassroomBranchQuery {

  /**
   * Whether the copy already holds members of the classroom in a branch other than {@code
   * branchId}. A classroom without any member is unknown here and reported as not foreign, so an
   * empty new classroom can still be scheduled.
   */
  boolean isKnownInOtherBranch(UUID classroomId, UUID branchId);
}
