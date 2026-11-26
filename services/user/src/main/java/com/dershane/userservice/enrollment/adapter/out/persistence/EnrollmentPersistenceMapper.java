package com.dershane.userservice.enrollment.adapter.out.persistence;

import com.dershane.userservice.enrollment.domain.ClassroomMembership;
import com.dershane.userservice.enrollment.domain.TeacherAssignment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper
interface EnrollmentPersistenceMapper {

  ClassroomMembership toDomain(MembershipJpaEntity entity);

  MembershipJpaEntity toEntity(ClassroomMembership membership);

  /** Only the end date changes (a membership is closed, never edited otherwise). */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "studentId", ignore = true)
  @Mapping(target = "classroomId", ignore = true)
  @Mapping(target = "startDate", ignore = true)
  void update(@MappingTarget MembershipJpaEntity entity, ClassroomMembership membership);

  /** {@code withWeeklyHours} returns the same type, so MapStruct would take it for a setter. */
  @Mapping(target = "withWeeklyHours", ignore = true)
  TeacherAssignment toDomain(TeacherAssignmentJpaEntity entity);

  TeacherAssignmentJpaEntity toEntity(TeacherAssignment assignment);

  /**
   * Only the weekly hours change; the teacher, classroom and subject of an assignment are fixed.
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "teacherId", ignore = true)
  @Mapping(target = "classroomId", ignore = true)
  @Mapping(target = "subjectId", ignore = true)
  void update(@MappingTarget TeacherAssignmentJpaEntity entity, TeacherAssignment assignment);
}
