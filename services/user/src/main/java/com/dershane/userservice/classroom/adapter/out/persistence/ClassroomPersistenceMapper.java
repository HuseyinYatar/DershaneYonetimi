package com.dershane.userservice.classroom.adapter.out.persistence;

import com.dershane.userservice.classroom.application.port.in.ClassroomView;
import com.dershane.userservice.classroom.domain.Classroom;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper
interface ClassroomPersistenceMapper {

  Classroom toDomain(ClassroomJpaEntity entity);

  ClassroomJpaEntity toEntity(Classroom classroom);

  /** Id, branch and grade level never change; version and audit fields are managed by JPA. */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "branchId", ignore = true)
  @Mapping(target = "gradeLevel", ignore = true)
  void update(@MappingTarget ClassroomJpaEntity entity, Classroom classroom);

  @Mapping(target = "activeStudentCount", ignore = true)
  ClassroomView toView(ClassroomJpaEntity entity);
}
