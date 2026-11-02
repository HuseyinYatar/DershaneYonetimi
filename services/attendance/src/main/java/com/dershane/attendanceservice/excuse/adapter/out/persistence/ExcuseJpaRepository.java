package com.dershane.attendanceservice.excuse.adapter.out.persistence;

import com.dershane.attendanceservice.excuse.domain.ExcuseStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

interface ExcuseJpaRepository
    extends JpaRepository<ExcuseJpaEntity, UUID>, JpaSpecificationExecutor<ExcuseJpaEntity> {

  @Query(
      """
      select e from ExcuseJpaEntity e
      where e.studentId in :studentIds and e.status = :status
        and e.fromDate <= :date and e.toDate >= :date
      order by e.decidedAt, e.id
      """)
  List<ExcuseJpaEntity> findCovering(
      Collection<UUID> studentIds, ExcuseStatus status, LocalDate date);
}
