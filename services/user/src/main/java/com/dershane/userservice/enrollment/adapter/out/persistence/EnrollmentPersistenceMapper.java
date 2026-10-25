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

  TeacherAssignment toDomain(TeacherAssignmentJpaEntity entity);

  TeacherAssignmentJpaEntity toEntity(TeacherAssignment assignment);
}
