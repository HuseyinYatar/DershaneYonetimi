package com.dershane.userservice.branch.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface BranchJpaRepository extends JpaRepository<BranchJpaEntity, UUID> {

  boolean existsByCode(String code);

  @Query("select b from BranchJpaEntity b where b.id = :id")
  Page<BranchJpaEntity> findPageById(UUID id, Pageable pageable);
}
