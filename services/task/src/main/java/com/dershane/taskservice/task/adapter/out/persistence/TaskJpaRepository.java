package com.dershane.taskservice.task.adapter.out.persistence;

import com.dershane.taskservice.task.domain.TargetType;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

interface TaskJpaRepository
    extends JpaRepository<TaskJpaEntity, UUID>, JpaSpecificationExecutor<TaskJpaEntity> {

  @Query(
      """
      select t from TaskJpaEntity t
      where t.active = true and t.startDate <= :date
        and (t.endDate is null or t.endDate >= :date)
      order by t.id
      """)
  List<TaskJpaEntity> findActiveOn(LocalDate date);

  @Query(
      """
      select t from TaskJpaEntity t
      where t.active = true and t.targetType = :targetType and t.targetId = :targetId
        and t.startDate <= :date and (t.endDate is null or t.endDate >= :date)
      order by t.id
      """)
  List<TaskJpaEntity> findActiveTargeting(TargetType targetType, UUID targetId, LocalDate date);
}
