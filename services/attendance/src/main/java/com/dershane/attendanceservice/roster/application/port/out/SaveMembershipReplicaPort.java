package com.dershane.attendanceservice.roster.application.port.out;

import com.dershane.attendanceservice.roster.domain.MembershipReplica;

public interface SaveMembershipReplicaPort {

  /** Inserts a new replica or updates a loaded one. */
  void save(MembershipReplica membership);
}
