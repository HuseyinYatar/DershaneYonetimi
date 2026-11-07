package com.dershane.taskservice.roster.application.port.out;

import com.dershane.taskservice.roster.domain.MembershipReplica;

public interface SaveMembershipReplicaPort {

  /** Inserts a new replica or updates a loaded one. */
  void save(MembershipReplica membership);
}
