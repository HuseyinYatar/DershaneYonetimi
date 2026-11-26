package com.dershane.attendanceservice.schedule.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface TimetableDraftJpaRepository extends JpaRepository<TimetableDraftJpaEntity, UUID> {

  Optional<TimetableDraftJpaEntity> findByBranchId(UUID branchId);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("delete from TimetableDraftJpaEntity d where d.id = :id")
  void deleteDraft(UUID id);
}
