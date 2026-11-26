package com.dershane.attendanceservice.schedule.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface TimetableDraftMissingJpaRepository
    extends JpaRepository<TimetableDraftMissingJpaEntity, UUID> {

  List<TimetableDraftMissingJpaEntity> findByDraftId(UUID draftId);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("delete from TimetableDraftMissingJpaEntity m where m.draftId = :draftId")
  void deleteByDraftId(UUID draftId);
}
