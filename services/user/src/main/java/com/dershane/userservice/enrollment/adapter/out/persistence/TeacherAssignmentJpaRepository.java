package com.dershane.userservice.enrollment.adapter.out.persistence;

import com.dershane.userservice.enrollment.application.port.in.ClassroomSummaryView;
import com.dershane.userservice.enrollment.application.port.in.ClassroomTeacherView;
import com.dershane.userservice.enrollment.application.port.in.TeacherAssignmentView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * The read queries join the classroom, subject and teacher tables by entity name. The module never
 * touches their repositories; the join is read-side only.
 */
interface TeacherAssignmentJpaRepository extends JpaRepository<TeacherAssignmentJpaEntity, UUID> {

  boolean existsByTeacherIdAndClassroomIdAndSubjectId(
      UUID teacherId, UUID classroomId, UUID subjectId);

  boolean existsByTeacherIdAndClassroomId(UUID teacherId, UUID classroomId);

  Optional<TeacherAssignmentJpaEntity> findByIdAndTeacherId(UUID id, UUID teacherId);

  /** True if the teacher teaches the student's current classroom. */
  @Query(
      """
      select count(a) > 0
      from TeacherAssignmentJpaEntity a, MembershipJpaEntity m
      where a.teacherId = :teacherId
        and m.classroomId = a.classroomId
        and m.studentId = :studentId
        and m.endDate is null
      """)
  boolean teachesStudent(UUID teacherId, UUID studentId);

  @Query(
      """
      select new com.dershane.userservice.enrollment.application.port.in.ClassroomTeacherView(
          a.id, t.id, t.info.firstName, t.info.lastName, s.id, s.name)
      from TeacherAssignmentJpaEntity a
        join TeacherJpaEntity t on t.id = a.teacherId
        join SubjectJpaEntity s on s.id = a.subjectId
      where a.classroomId = :classroomId
      order by s.name, t.info.lastName
      """)
  List<ClassroomTeacherView> findTeachersOfClassroom(UUID classroomId);

  @Query(
      """
      select new com.dershane.userservice.enrollment.application.port.in.TeacherAssignmentView(
          a.id, a.teacherId, c.id, c.name, s.id, s.name, a.createdAt)
      from TeacherAssignmentJpaEntity a
        join ClassroomJpaEntity c on c.id = a.classroomId
        join SubjectJpaEntity s on s.id = a.subjectId
      where a.teacherId = :teacherId
      order by c.term desc, c.name, s.name
      """)
  List<TeacherAssignmentView> findAssignmentsOfTeacher(UUID teacherId);

  @Query(
      """
      select new com.dershane.userservice.enrollment.application.port.in.TeacherAssignmentView(
          a.id, a.teacherId, c.id, c.name, s.id, s.name, a.createdAt)
      from TeacherAssignmentJpaEntity a
        join ClassroomJpaEntity c on c.id = a.classroomId
        join SubjectJpaEntity s on s.id = a.subjectId
      where a.id = :assignmentId
      """)
  Optional<TeacherAssignmentView> findAssignmentView(UUID assignmentId);

  @Query(
      """
      select new com.dershane.userservice.enrollment.application.port.in.ClassroomSummaryView(
          c.id, c.branchId, c.name, c.gradeLevel, c.term, c.active, c.createdAt, c.updatedAt)
      from ClassroomJpaEntity c
      where c.active = true
        and c.id in (select a.classroomId from TeacherAssignmentJpaEntity a
                     where a.teacherId = :teacherId)
      order by c.term desc, c.name
      """)
  List<ClassroomSummaryView> findActiveClassroomsOfTeacher(UUID teacherId);
}
