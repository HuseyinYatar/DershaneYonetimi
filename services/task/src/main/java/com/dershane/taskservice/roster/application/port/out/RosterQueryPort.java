package com.dershane.taskservice.roster.application.port.out;

import com.dershane.taskservice.roster.application.port.in.ActiveMembershipView;
import com.dershane.taskservice.roster.application.port.in.RosterEntryView;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RosterQueryPort {

  List<RosterEntryView> findRosterOn(UUID classroomId, LocalDate date);

  boolean existsMembershipOn(UUID classroomId, UUID studentId, LocalDate date);

  Optional<UUID> findClassroomBranchOn(UUID classroomId, LocalDate date);

  Optional<ActiveMembershipView> findActiveMembership(UUID studentId, LocalDate date);
}
