package com.dershane.attendanceservice.schedule.adapter.out.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

  /**
   * Takes the branch's advisory lock for the rest of the transaction. The wrapping select exists
   * only because the lock function returns void, which a native query cannot read directly.
   */
  @Query(
      value =
          """
          select count(*) from (
            select pg_advisory_xact_lock(
              cast(cast('x' || substr(md5(cast(:key as text)), 1, 16) as bit(64)) as bigint))
          ) locked
          """,
      nativeQuery = true)
  long lockBranch(@Param("key") String key);
}
