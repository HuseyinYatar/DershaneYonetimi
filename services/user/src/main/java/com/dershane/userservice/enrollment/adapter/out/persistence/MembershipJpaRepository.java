package com.dershane.userservice.enrollment.adapter.out.persistence;

import com.dershane.userservice.enrollment.application.port.in.ClassroomStudentView;
import com.dershane.userservice.enrollment.application.port.in.ClassroomSummaryView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * The read queries join the student and classroom tables by entity name. The module never touches
 * their repositories; the join is read-side only.
 */
interface MembershipJpaRepository extends JpaRepository<MembershipJpaEntity, UUID> {

  Optional<MembershipJpaEntity> findByStudentIdAndEndDateIsNull(UUID studentId);

  long countByClassroomIdAndEndDateIsNull(UUID classroomId);

  boolean existsByClassroomIdAndEndDateIsNull(UUID classroomId);

  boolean existsByStudentIdAndClassroomIdAndEndDateIsNull(UUID studentId, UUID classroomId);

  @Query(
      """
      select new com.dershane.userservice.enrollment.application.port.in.ClassroomStudentView(
          s.id, s.info.firstName, s.info.lastName, s.studentNumber, m.id, m.startDate)
      from MembershipJpaEntity m join StudentJpaEntity s on s.id = m.studentId
      where m.classroomId = :classroomId and m.endDate is null
      order by s.info.lastName, s.info.firstName
      """)
  List<ClassroomStudentView> findActiveStudents(UUID classroomId);

  @Query(
      """
      select new com.dershane.userservice.enrollment.application.port.in.ClassroomSummaryView(
          c.id, c.branchId, c.name, c.gradeLevel, c.term, c.active, c.createdAt, c.updatedAt)
      from ClassroomJpaEntity c
      where c.id in (
          select m.classroomId from MembershipJpaEntity m
          where m.studentId = :studentId and m.endDate is null)
      """)
  Optional<ClassroomSummaryView> findClassroomOfStudent(UUID studentId);
}
