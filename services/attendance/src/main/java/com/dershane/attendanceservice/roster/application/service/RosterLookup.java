package com.dershane.attendanceservice.roster.application.service;

import com.dershane.attendanceservice.roster.application.port.in.ClassroomBranchQuery;
import com.dershane.attendanceservice.roster.application.port.in.ClassroomRosterQuery;
import com.dershane.attendanceservice.roster.application.port.in.GuardianAccessQuery;
import com.dershane.attendanceservice.roster.application.port.in.MembershipQuery;
import com.dershane.attendanceservice.roster.application.port.in.RosterEntryView;
import com.dershane.attendanceservice.roster.application.port.out.LoadGuardianLinkPort;
import com.dershane.attendanceservice.roster.application.port.out.RosterQueryPort;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Read-only answers other modules ask about memberships and guardians. */
@Component
@RequiredArgsConstructor
class RosterLookup
    implements ClassroomRosterQuery, ClassroomBranchQuery, MembershipQuery, GuardianAccessQuery {

  private final RosterQueryPort rosterQuery;
  private final LoadGuardianLinkPort links;

  @Override
  @Transactional(readOnly = true)
  public List<RosterEntryView> rosterOn(UUID classroomId, UUID branchId, LocalDate date) {
    return rosterQuery.findRosterOn(classroomId, branchId, date);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean isKnownInOtherBranch(UUID classroomId, UUID branchId) {
    return rosterQuery.existsMembershipInOtherBranch(classroomId, branchId);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean wasMemberOn(UUID classroomId, UUID branchId, UUID studentId, LocalDate date) {
    return rosterQuery.existsMembershipOn(classroomId, branchId, studentId, date);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean isGuardianOf(UUID guardianId, UUID studentId) {
    return links.existsByGuardianIdAndStudentId(guardianId, studentId);
  }
}
