package com.dershane.attendanceservice.roster.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface GuardianLinkReplicaJpaRepository
    extends JpaRepository<GuardianLinkReplicaJpaEntity, UUID> {

  boolean existsByGuardianIdAndStudentId(UUID guardianId, UUID studentId);
}
