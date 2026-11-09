package com.dershane.attendanceservice.session.adapter.out.persistence;

import com.dershane.attendanceservice.session.domain.SessionStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

interface ClassSessionJpaRepository
    extends JpaRepository<ClassSessionJpaEntity, UUID>,
        JpaSpecificationExecutor<ClassSessionJpaEntity> {

  boolean existsByScheduleIdAndSessionDate(UUID scheduleId, LocalDate sessionDate);

  List<ClassSessionJpaEntity> findByStatusAndSessionDateBefore(
      SessionStatus status, LocalDate sessionDate);

  /** Two ranges overlap when each starts before the other ends. */
  @Query(
      """
      select count(s) > 0 from ClassSessionJpaEntity s
      where s.branchId = :branchId and s.classroomId = :classroomId and s.sessionDate = :date
        and s.status in :statuses and s.id <> :excludeId
        and s.startsAt < :endsAt and s.endsAt > :startsAt
      """)
  boolean existsClassroomOverlap(
      UUID branchId,
      UUID classroomId,
      LocalDate date,
      LocalTime startsAt,
      LocalTime endsAt,
      Collection<SessionStatus> statuses,
      UUID excludeId);

  @Query(
      """
      select count(s) > 0 from ClassSessionJpaEntity s
      where s.branchId = :branchId and s.teacherId = :teacherId and s.sessionDate = :date
        and s.status in :statuses and s.id <> :excludeId
        and s.startsAt < :endsAt and s.endsAt > :startsAt
      """)
  boolean existsTeacherOverlap(
      UUID branchId,
      UUID teacherId,
      LocalDate date,
      LocalTime startsAt,
      LocalTime endsAt,
      Collection<SessionStatus> statuses,
      UUID excludeId);
}
