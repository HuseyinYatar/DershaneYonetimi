package com.dershane.attendanceservice.roster.application.port.out;

import com.dershane.attendanceservice.roster.application.port.in.RosterEntryView;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface RosterQueryPort {

  List<RosterEntryView> findRosterOn(UUID classroomId, UUID branchId, LocalDate date);

  boolean existsMembershipOn(UUID classroomId, UUID branchId, UUID studentId, LocalDate date);

  /** The replica holds a member of the classroom whose branch is not {@code branchId}. */
  boolean existsMembershipInOtherBranch(UUID classroomId, UUID branchId);
}
