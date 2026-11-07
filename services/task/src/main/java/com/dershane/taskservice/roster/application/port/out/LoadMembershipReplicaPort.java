package com.dershane.taskservice.roster.application.port.out;

import com.dershane.taskservice.roster.domain.MembershipReplica;
import java.util.Optional;
import java.util.UUID;

public interface LoadMembershipReplicaPort {

  Optional<MembershipReplica> findById(UUID membershipId);

  /** Branch of the student's most recent membership, if the student has any. */
  Optional<UUID> findBranchIdOfStudent(UUID studentId);
}
