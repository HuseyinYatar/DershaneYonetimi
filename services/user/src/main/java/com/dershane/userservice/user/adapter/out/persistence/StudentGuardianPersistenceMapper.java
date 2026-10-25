package com.dershane.userservice.user.adapter.out.persistence;

import com.dershane.userservice.user.domain.StudentGuardian;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper
interface StudentGuardianPersistenceMapper {

  StudentGuardian toDomain(StudentGuardianJpaEntity entity);

  StudentGuardianJpaEntity toEntity(StudentGuardian link);

  /** The key (guardian, student) never changes; only the relationship does. */
  @Mapping(target = "guardianId", ignore = true)
  @Mapping(target = "studentId", ignore = true)
  void update(@MappingTarget StudentGuardianJpaEntity entity, StudentGuardian link);
}
