package com.dershane.attendanceservice.schedule.application.port.out;

import java.util.List;
import java.util.UUID;

/** What the timetable needs from the user service: who teaches what, and how many hours a week. */
public interface UserDirectoryPort {

  /** An assignment of a branch's active classroom; {@code weeklyHours} 0 means not planned. */
  record BranchAssignment(UUID teacherId, UUID classroomId, UUID subjectId, int weeklyHours) {}

  /**
   * Asked on behalf of the caller (their token is forwarded), so the user service decides access.
   */
  List<BranchAssignment> loadBranchAssignments(UUID branchId);
}
