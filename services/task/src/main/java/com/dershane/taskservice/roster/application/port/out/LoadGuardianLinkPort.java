package com.dershane.taskservice.roster.application.port.out;

import com.dershane.taskservice.roster.domain.GuardianLinkReplica;
import java.util.Optional;
import java.util.UUID;

public interface LoadGuardianLinkPort {

  Optional<GuardianLinkReplica> findByStudentId(UUID studentId);

  boolean existsByGuardianIdAndStudentId(UUID guardianId, UUID studentId);
}
