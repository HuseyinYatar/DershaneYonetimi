package com.dershane.attendanceservice.schedule.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface TimetableDraftLessonJpaRepository
    extends JpaRepository<TimetableDraftLessonJpaEntity, UUID> {

  List<TimetableDraftLessonJpaEntity> findByDraftIdOrderByDayOfWeekAscStartsAtAsc(UUID draftId);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("delete from TimetableDraftLessonJpaEntity l where l.draftId = :draftId")
  void deleteByDraftId(UUID draftId);
}
