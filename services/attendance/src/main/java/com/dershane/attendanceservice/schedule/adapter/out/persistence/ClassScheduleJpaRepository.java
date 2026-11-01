package com.dershane.attendanceservice.schedule.adapter.out.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

interface ClassScheduleJpaRepository
    extends JpaRepository<ClassScheduleJpaEntity, UUID>,
        JpaSpecificationExecutor<ClassScheduleJpaEntity> {

  @Query(
      """
      select s from ClassScheduleJpaEntity s
      where s.active = true and s.validFrom <= :to and (s.validTo is null or s.validTo >= :from)
      order by s.id
      """)
  List<ClassScheduleJpaEntity> findActiveValidBetween(LocalDate from, LocalDate to);
}
