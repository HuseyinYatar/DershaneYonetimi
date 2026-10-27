package com.dershane.userservice.classroom.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface ClassroomJpaRepository
    extends JpaRepository<ClassroomJpaEntity, UUID>, JpaSpecificationExecutor<ClassroomJpaEntity> {

  boolean existsByBranchIdAndActiveTrue(UUID branchId);

  boolean existsByBranchIdAndTermAndName(UUID branchId, String term, String name);
}
