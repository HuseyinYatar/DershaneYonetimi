package com.dershane.taskservice.roster.application.port.out;

import com.dershane.taskservice.roster.domain.GuardianLinkReplica;
import java.util.UUID;

public interface SaveGuardianLinkPort {

  /** Inserts a new link or updates a loaded one. */
  void save(GuardianLinkReplica link);

  void deleteByStudentId(UUID studentId);
}
