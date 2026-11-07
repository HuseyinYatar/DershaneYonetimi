package com.dershane.taskservice.assignment.adapter.out.persistence;

import com.dershane.taskservice.assignment.domain.AssignmentStatus;
import com.dershane.taskservice.task.domain.TargetType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * The classroom query reads the task table by entity name. The module never touches the task
 * repository; the sub-select is read-side only.
 */
interface AssignmentJpaRepository extends JpaRepository<AssignmentJpaEntity, UUID> {

  @Query(
      "select a.studentId from AssignmentJpaEntity a"
          + " where a.taskId = :taskId and a.occurrenceDate = :occurrenceDate")
  List<UUID> findStudentIds(UUID taskId, LocalDate occurrenceDate);

  @Query(
      """
      select a from AssignmentJpaEntity a
      where a.studentId = :studentId and a.status = :status and a.dueAt > :now
        and a.taskId in (
          select t.id from TaskJpaEntity t
          where t.targetType = :targetType and t.targetId = :classroomId)
      order by a.id
      """)
  List<AssignmentJpaEntity> findOpenForTarget(
      UUID studentId,
      UUID classroomId,
      TargetType targetType,
      AssignmentStatus status,
      Instant now);

  @Query(
      """
      select a from AssignmentJpaEntity a
      where a.status = :status and a.dueAt <= :now
      order by a.dueAt, a.id
      """)
  List<AssignmentJpaEntity> findDueBefore(AssignmentStatus status, Instant now, Pageable pageable);

  /**
   * Native because the window arithmetic ({@code assigned_at <= due_at - lead}) must run in the
   * database, or already-ineligible rows would fill every batch.
   */
  @Query(
      value =
          """
          select * from assignment
          where status = 'ASSIGNED' and reminder_sent = false
            and due_at > :now and due_at <= :windowEnd
            and assigned_at <= due_at - make_interval(secs => :leadSeconds)
          order by due_at, id
          limit :limit
          """,
      nativeQuery = true)
  List<AssignmentJpaEntity> findReminderCandidates(
      Instant now, Instant windowEnd, double leadSeconds, int limit);
}
