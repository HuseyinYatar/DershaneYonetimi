package com.dershane.taskservice.roster.application.service;

import com.dershane.taskservice.roster.application.port.in.ActiveMembershipView;
import com.dershane.taskservice.roster.application.port.in.ClassroomRosterQuery;
import com.dershane.taskservice.roster.application.port.in.GuardianAccessQuery;
import com.dershane.taskservice.roster.application.port.in.MembershipQuery;
import com.dershane.taskservice.roster.application.port.in.RosterEntryView;
import com.dershane.taskservice.roster.application.port.out.LoadGuardianLinkPort;
import com.dershane.taskservice.roster.application.port.out.RosterQueryPort;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Read-only answers other modules ask about memberships and guardians. */
@Component
@RequiredArgsConstructor
class RosterLookup implements ClassroomRosterQuery, MembershipQuery, GuardianAccessQuery {

  private final RosterQueryPort rosterQuery;
  private final LoadGuardianLinkPort links;

  @Override
  @Transactional(readOnly = true)
  public List<RosterEntryView> rosterOn(UUID classroomId, LocalDate date) {
    return rosterQuery.findRosterOn(classroomId, date);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean wasMemberOn(UUID classroomId, UUID studentId, LocalDate date) {
    return rosterQuery.existsMembershipOn(classroomId, studentId, date);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UUID> branchOfClassroomOn(UUID classroomId, LocalDate date) {
    return rosterQuery.findClassroomBranchOn(classroomId, date);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ActiveMembershipView> activeMembershipOf(UUID studentId, LocalDate date) {
    return rosterQuery.findActiveMembership(studentId, date);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean isGuardianOf(UUID guardianId, UUID studentId) {
    return links.existsByGuardianIdAndStudentId(guardianId, studentId);
  }
}
